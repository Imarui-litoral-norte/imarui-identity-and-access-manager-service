# Integração de serviços com o PDP

O consumidor é backend confiável e atua como PEP. Não exponha credencial técnica no navegador,
não aceite Actor ou Scope arbitrários do frontend e nunca interprete falha de rede como autorização.
O IIAM não habilita aplicações por Tenant nem substitui a propriedade do recurso no consumidor.

## Preparação administrativa

1. O administrador central cadastra catalog owner e namespace em `/api/v1/authorization/admin/catalog/owners`.
2. O consumidor gera RSA 2048–4096 bits e protege a chave privada em seu gerenciador de segredos.
3. O administrador registra somente a chave pública SPKI em Base64, prazo até 90 dias,
   operações PDP_DECIDE e/ou CATALOG_SYNC e raízes autorizadas em `/admin/service-clients`.
   O prefixo completo é `/api/v1/authorization`. As operações exigem Permissions administrativas
   e escopo real; o retorno contém client ID, key ID e fingerprints, sem material privado.
4. O serviço publica seu manifesto via `/service/catalog`. Aprovação de delegação inicial,
   composição de Roles e atribuições são passos administrativos separados: publicar não concede acesso.

Rotação adiciona chave nova, distribui o novo kid no consumidor, valida funcionamento e revoga
a antiga após sobreposição. Revogação ou desabilitação do cliente afeta a próxima consulta,
independentemente da validade da prova/JWT anterior. Não automatizar concessão de jurisdições.

## Prova de requisição

Use HTTPS validado, `Content-Type: application/json` UTF-8 e `Authorization: Service <prova>`.
A prova é JWS compacta RS256, header `typ=iiam-service+jwt`, `kid=<UUID da chave>` e claims:

| Claim | Valor |
| --- | --- |
| iss / sub | Código do serviço cadastrado; iguais |
| aud | Audience humana configurada no IIAM acrescida de `:services` |
| iat / exp | Instante atual / até 60 segundos; relógios sincronizados |
| jti | Novo UUID para cada tentativa, consumido uma única vez |
| htm | POST |
| htu | Path exato `/api/v1/authorization/service/decisions`, `/decisions/batch` ou `/catalog` |
| body_sha256 | SHA-256 dos bytes exatos UTF-8 do corpo, Base64url sem padding |
| correlation_id | UUID; igual à correlação do payload PDP/batch |

Não enviar query string, compressão, charset alternativo, JWK embutida, jku ou extensões críticas.
Limite do corpo técnico: 64 KiB. Não serializar novamente o corpo depois de assinar seu digest.
Replay ou alteração de método/path/corpo falha. Não reutilizar uma prova em retry.

## Consulta simples

POST `/api/v1/authorization/service/decisions`:

```json
{
  "actorToken": "<JWT humano recebido pelo backend>",
  "identityId": "00000000-0000-0000-0000-000000000001",
  "permission": "orders.order.read",
  "resourceType": "TENANT",
  "resourceId": "00000000-0000-0000-0000-000000000002",
  "expectedScopeId": null,
  "correlationId": "00000000-0000-0000-0000-000000000003"
}
```

Os UUIDs são exemplos. `identityId` é o subject do JWT, nunca um usuário escolhido pelo operador
do serviço. O backend resolve order ID → Tenant proprietário em sua fonte confiável e envia esse
Tenant como resourceId; o IIAM resolve Tenant → Scope. Se o backend também conhece o Scope
oficial, pode fornecer `expectedScopeId` para detectar divergência. Um objeto externo sem Tenant
pode usar binding técnico imutável cadastrado sob administração do IIAM; nunca um Scope livre.

HTTP 200 contém `allowed=true`, `reasonCode=ALLOW`, decisionId, correlationId e evaluatedAt.
DENY de política usa HTTP 403 com os mesmos campos e `allowed=false`, `reasonCode=DENY`.
Não há proveniência de Roles nessa resposta. O endpoint administrativo `/decisions/explain`
exige AUTHORIZATION_AUDIT_READ sobre o Scope e revela motivo direto/herdado, Role e Scope de origem.

Batch: `{ "correlationId": "...", "decisions": [ ... ] }`, até 20 itens, todos com a mesma
correlação, Identity e JWT. Retorna HTTP 200 com lista de decisões individuais, inclusive DENY;
falhas estruturais/autenticação/jurisdição abortam a chamada. Não usar status 200 do batch como ALLOW.

## Aplicação da decisão e erros

| Resultado | Ação do PEP |
| --- | --- |
| 200 + decisão coerente ALLOW | Aplicar somente à operação/recurso consultados |
| 403 + DENY | Negar; não tentar outro Scope/Actor |
| 401 | Rejeitar contexto não autenticado; verificar sessão e credencial técnica |
| 403 ProblemDetail | Caller, ownership ou jurisdição não autorizados |
| 404 | Recurso/binding inexistente; não inferir autorização |
| 409 | Conflito de versão/manifesto administrativo; reler estado |
| 413 / 415 / 429 | Corrigir corpo/protocolo ou respeitar limite; negar a operação |
| 503 / timeout / TLS / JSON inválido / correlação divergente | Negar e registrar falha de integração |

Problemas usam `application/problem+json`, code, status e correlação. Nunca logar actorToken,
Authorization, senha ou corpo completo. Registrar decisionId/correlationId para investigação.
Use timeout total curto (exemplo 2 s), sem fallback permissivo, redirect ou cache de ALLOW.
Uma nova operação deve gerar nova consulta/prova; não há atomicidade distribuída com o banco do PEP.

## Cliente de referência Java 17

`br.com.imarui.ima.authorization.api.pep.AuthorizationPepClient` é referência de backend incluída
neste repositório, não um SDK publicado. Recebe origin HTTPS, service, audience, credentialId,
RSAPrivateKey protegida pelo consumidor, `TrustedResourceResolver`, timeout e Clock.
O resolver recebe somente a chave de negócio do recurso, consulta seu proprietário e devolve
`Resource("TENANT", tenantId.toString(), expectedScopeId)`. `check(...)` produz Decision cujo
`allowed` precisa ser true antes de executar a operação. Falhas retornam allowed=false e Failure.
HTTP sem TLS é aceito apenas em loopback para testes. Há testes reais com HTTP stub para timeout,
redirect, erro operacional e resposta adulterada, além de integração contra o PDP do IIAM.

## Contrato do catálogo

POST `/service/catalog` usa a mesma prova técnica, com CATALOG_SYNC:

```json
{
  "owner": "orders",
  "namespace": "orders",
  "version": 1,
  "permissions": [{"code":"orders.order.read","description":"Consultar pedido","module":"orders","deprecated":false}]
}
```

Manifesto é completo. Omitir código existente deprecia; não apaga histórico nem muda seu ID.
Resposta `changed=false` é repetição idêntica. Versão reutilizada com digest diferente resulta
em 409; owner/namespace alheio resulta em negação. Inspeção de owners, revisões e manifesto
histórico exige auditoria global; o serviço técnico não recebe acesso a políticas humanas.
Consulte o [OpenAPI exportado](openapi.json), gerado do aplicativo testado, para DTOs e rotas completas.
