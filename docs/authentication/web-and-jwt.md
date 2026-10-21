# Contrato web e JWT/JWKS

## Sequência web

1. `GET /authentication/csrf` com `credentials: include`; guardar o `token` retornado em memória.
2. `POST /authentication/login`, com cookie CSRF, header `X-XSRF-TOKEN` e JSON `{ "identityKind":"SERVICE", "identifier":"my-integration", "password":"<senha>" }`.
3. Resposta: `{ "accessToken":"<JWT>", "tokenType":"Bearer", "expiresIn":900 }`. Guardar access em memória. Refresh é somente `Set-Cookie: IIAM_REFRESH=...; Path=/authentication; Secure; HttpOnly; SameSite=Lax`.
4. Enviar `Authorization: Bearer <access>` às APIs protegidas.
5. `POST /authentication/refresh` diretamente no IIAM, com cookies e header CSRF. Recebe outro access e um novo cookie refresh. Não existe refresh em DTO, query string ou localStorage.
6. `POST /authentication/logout` diretamente no IIAM, com cookie/CSRF, sem precisar de backend intermediário. Pode apresentar Bearer válido adicionalmente. Resposta 204 limpa o cookie com os mesmos atributos.

O frontend deve serializar renovações concorrentes, inclusive coordenando abas. Duas requisições com o mesmo refresh não recebem dois sucessores utilizáveis: replay revoga a sessão. Após 401, limpar access em memória e exigir login.

Os cookies são host-only: Domain deliberadamente omitido. Portas diferentes no mesmo host exigem allowlist CORS; compartilhar IP/máquina não elimina políticas de origem. O Path cobre login/refresh/logout. Secure permanece ativo em todos os perfis: teste HTTP inspeciona/reenvia cookies explicitamente; o navegador real deve usar HTTPS, inclusive em homologação. SameSite=Lax é o padrão para mesma máquina/site. Se a topologia mudar para sites distintos, configurar None e validar restrições de cookies de terceiros; não habilitar CORS wildcard com credentials.

## Primeiro acesso, recuperação e senha

- `POST /authentication/activation/request` com `{identityKind,identifier}`: 202 genérico. Contato disponível recebe convite; ausência de conta/contato não é revelada.
- `POST /authentication/activation/complete` com `{token,password}`: 204; consome convite de ativação, persiste credencial e PENDING→ACTIVE atomicamente. Não inicia sessão automaticamente.
- SERVICE local ou externo utiliza o mesmo endpoint público de conclusão; prova inicial é convite criptográfico de uso único entregue previamente pelo operador. Conhecer nickname/UUID não permite obter o convite.
- Operador com `AUTHENTICATION_INVITATION_CREATE` pode chamar `POST /authentication/admin/identities/{id}/invitation`. Retorno no-store contém somente o convite recém-gerado para entrega segura. Nunca enviar essa resposta para logs de gateway.
- `POST /authentication/recovery/request` com `{identityKind,identifier}`: 202 genérico; e-mail é canal de contato. `POST /authentication/recovery/complete` com `{token,password}`: 204 e revogação de sessões anteriores.
- `POST /authentication/password` com Bearer e `{currentPassword,newPassword}`: exige senha atual; revoga todas as sessões, inclusive a atual.
- Senhas: mínimo 12 caracteres e máximo 72 bytes UTF-8, respeitando BCrypt. Convite de ativação vale 24 horas; recuperação vale 15 minutos. Reemissão invalida desafios anteriores da mesma finalidade.

## Sessões e erros

`GET /authentication/sessions` lista até 100 sessões do titular; `DELETE /authentication/sessions/{sid}` encerra somente sessão própria. Não se recebe IdentityId do titular no payload. As APIs `/authentication/admin/sessions` e `/authentication/admin/refresh-tokens` continuam protegidas por suas permissions existentes, com parâmetros opcionais page/size, tamanho máximo 100. Não expõem hashes nem tokens.

Erros usam `application/problem+json`, status, code e timestamp: 400 contrato inválido, 401 autenticação/challenge/refresh inválido, 403 acesso negado/CSRF, 404 recurso administrativo inexistente e 429 limite de tentativas. Mensagens públicas não distinguem conta ausente, PENDING, DISABLED ou senha incorreta.

## JWT

- Algoritmo exclusivamente RS256; header `typ=at+jwt` e `kid` derivado da chave pública.
- `iss`: configuração do IIAM; `aud`: audience estática/configurável, sem escolha livre pelo cliente.
- `sub`: UUID Identity; `sid`: UUID público Session; `jti`: UUID aleatório; `iat` e `exp`: NumericDate UTC, diferença 900 segundos.
- JWKS público em `/.well-known/jwks.json`, somente material público. Cache HTTP de cinco minutos; rotação deve manter chaves antigas de verificação por pelo menos o TTL de access mais a janela de cache dos consumidores.
- Session e refresh expiram absolutamente sete dias após login. A rotação mantém exatamente esse vencimento. Um access emitido perto do limite da sessão ainda pode durar 900s em consumidor offline; o próprio IIAM rejeita sessão expirada.
- Logout/disable/reset bloqueiam refresh e o acesso interno; um consumidor exclusivamente offline pode aceitar access já emitido até exp, no máximo 15 minutos. Não existe introspecção obrigatória nesta V1.

Exemplo conceitual para futuro Spring Resource Server:

```java
var decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
var timestamps = new JwtTimestampValidator(Duration.ZERO);
decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
    timestamps, new JwtIssuerValidator(expectedIssuer), audienceValidator));
// audienceValidator exige audience configurada; rejeitar ausência de sub/sid/jti e TTL > 900s.
// Somente após validação: UUID identityId = UUID.fromString(jwt.getSubject());
// UUID sid = UUID.fromString(jwt.getClaimAsString("sid"));
// Construir Actor local; nenhum identityId do payload substitui esse ator.
```

O exemplo é documentação evolutiva, não SDK nem serviço externo implantado. O consumidor também deve restringir algoritmo/tipo, validar claims mínimas e negar autorização até implementar sua política de domínio.
