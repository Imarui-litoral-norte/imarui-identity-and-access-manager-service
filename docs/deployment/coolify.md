# Deploy do IIAM no Coolify (produção)

Este runbook descreve a configuração do **serviço standalone** baseada na branch `main`. Não configura automaticamente o servidor, DNS, PostgreSQL, proxy ou segredos. Não usar os perfis `dev` ou `test` em produção.

## 1. Pré-requisitos e decisão sobre proxy HTTPS (gate obrigatório)

- Um PostgreSQL acessível pela rede privada do container, com backup/restauração testados antes de migrations em bases existentes.
- Um domínio HTTPS válido para a API e DNS/certificados configurados no proxy.
- O proxy deve ser o **único** caminho de acesso ao container; não publicar a porta HTTP 8080 diretamente na Internet.
- O `AuthenticationController` verifica `request.isSecure()` e `application.yaml` fixa `server.forward-headers-strategy: none`. Em terminação TLS no Traefik, as rotas de login, refresh, logout, ativação e recuperação poderão falhar com `HTTPS is required` se o Spring enxergar a conexão interna HTTP.
- **Não use `AUTH_REQUIRE_HTTPS=false` como workaround.** A solução com TLS terminado no proxy exige tratamento de cabeçalhos encaminhados **somente de proxies confiáveis**, com o endereço real do proxy verificado e cabeçalhos do cliente removidos/regravados na borda. O ajuste do Tomcat pode ser feito no ambiente com `SERVER_FORWARD_HEADERS_STRATEGY=native`, `SERVER_TOMCAT_REMOTEIP_PROTOCOL_HEADER=x-forwarded-proto` e `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=<regex restrita aos IPs reais do proxy>`, **após** auditoria da topologia. Nunca confiar em todos os IPs ou aceitar `X-Forwarded-Proto` diretamente de clientes. Em caso de dúvida, manter este gate como **NO-GO** para autenticação externa.
- Valide efetivamente o login/refresh por HTTPS após configurar a cadeia proxy→aplicação. `/actuator/health` sozinho não valida essa integração.

## 2. Aplicação

No Coolify: `Projects` → projeto/ambiente da Imaruí → `+ New` → repositório GitHub público/Git App → `Imarui-litoral-norte/imarui-identity-and-access-manager-service` → branch `main` (depois do merge) → Build strategy `Dockerfile`.

- Base directory: raiz do repositório.
- Dockerfile location: `/Dockerfile` (ajustar à convenção de caminho mostrada pela UI).
- Exposed port: `8080`. O processo escuta na porta interna 8080; o domínio público usa HTTPS no proxy.
- Runtime: `SPRING_PROFILES_ACTIVE=prod` já é o default no Dockerfile. Evite sobrescrever.
- Healthcheck: o Dockerfile já testa `http://127.0.0.1:8080/actuator/health` com curl e start period de 120 s. Se ativar o healthcheck do painel Coolify, este **substitui** o do Dockerfile; mantenha a mesma rota/porta e verifique a imagem.
- Não compartilhar volume do JAR nem acoplar o banco ao filesystem efêmero do container. Configure limites de memória/CPU adequados ao servidor.

## 3. Variáveis no painel (valores de exemplo, nunca copiar credenciais reais para o Git)

Configurar em `Environment Variables` como **runtime**; segredos protegidos no painel/secret manager. Valores entre `<...>` são placeholders.

```dotenv
DB_URL=jdbc:postgresql://<postgres-internal-host>:5432/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-secret>
JWT_PRIVATE_KEY_FILE=/run/secrets/iiam-private.pem
JWT_ISSUER=https://<iam-domain>
JWT_AUDIENCE=imarui-iam
APP_BASE_URL=https://<iam-domain>
AUTH_ALLOWED_ORIGINS=https://<frontend-domain>
AUTH_REQUIRE_HTTPS=true
AUTH_COOKIE_SAME_SITE=Lax
SECURITY_AUTH_FRONTEND_URL=https://<frontend-domain>
SPRING_MAIL_HOST=<smtp-host>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<smtp-user>
SPRING_MAIL_PASSWORD=<smtp-secret>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
MANAGEMENT_HEALTH_REDIS_ENABLED=false
```

- `DB_URL` usa JDBC PostgreSQL; não é uma URL HTTP. Use hostname alcançável pela rede da aplicação.
- A aplicação assina tokens com RSA/RS256. `JWT_SECRET`/HS256 nos guias antigos **não** é o contrato de produção. `JWT_PRIVATE_KEY_FILE` deve apontar para um arquivo, não para o próprio texto PEM. Preserve a chave entre reinícios e deploys; rotação exige sobreposição de públicas via `JWT_VERIFICATION_KEY_FILES`.
- `AUTH_ALLOWED_ORIGINS` recebe origens exatas, separadas por vírgulas; sem wildcard. `AUTH_COOKIE_SAME_SITE=Lax` só faz sentido se a topologia real de frontend/API permitir; revise o contrato de cookie/CSRF para sites distintos.
- `SECURITY_AUTH_FRONTEND_URL` precisa ser HTTPS para os links enviados pelo adapter `SmtpChallengeMail`. SMTP real deve ser validado com o provedor. A ausência de SMTP pode não derrubar o boot, mas compromete envio de convites e recuperação.
- O projeto inclui starter Redis, porém os limitadores documentados usam PostgreSQL e Redis não é obrigação operacional do fluxo atual. Desabilitar **somente o indicador Redis do healthcheck** se o serviço efetivamente não depender de Redis. Não tratar isto como remoção de eventual dependência real.
- O valor `MANAGEMENT_HEALTH_REDIS_ENABLED=false` evita um status `DOWN` causado por healthcheck Redis sem instância. Valide as demais dependências e o resultado completo do healthcheck.

## 4. Arquivo de assinatura RSA

Gerar PEM privado **fora do Git** (PKCS8, recomendado RSA 3072 bits). Exemplo no host protegido:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /srv/iiam-secrets/iiam-private.pem
```

Monte este arquivo via `Persistent Storage` → `Host File Mount`: origem `/srv/iiam-secrets/iiam-private.pem` → destino `/run/secrets/iiam-private.pem`, preferencialmente somente leitura quando disponível. O processo no Dockerfile roda com UID/GID `10001:10001` e precisa conseguir ler o arquivo; ajuste permissões de forma restrita ao host. Não exponha o conteúdo no painel, logs, artefatos de CI, imagem ou repositório. Arquivo protegido apenas por root (`0600`) **não será legível** por UID 10001 sem configuração apropriada de ACL/grupo.

## 5. Banco e bootstrap

O Flyway executa as migrations versionadas e o Hibernate valida o schema. Na `main` inspecionada existem migrations `V1` a `V27`. Para banco novo, provisionar credenciais com privilégios de migração. Para banco existente, **não** aplicar `baselineOnMigrate` automaticamente; realizar preflight, backup, simulação/restauração e ler `docs/database/README.md`.

Não há ROOT padrão criado automaticamente. Se ainda não existir operador inicial, o bootstrap é uma ação operacional **única**, com `security.bootstrap.enabled=true`, `security.bootstrap.input-file` e `security.bootstrap.output-file` apontando para arquivos/volume protegidos e graváveis pelo UID 10001. Consulte `docs/authentication/operations.md`; capture convite de maneira segura e desabilite o bootstrap após o uso. Evite ativá-lo num container que reinicia automaticamente sem supervisão.

## 6. Critérios para GO

1. Build do Dockerfile passa no CI; deployment logs mostram aplicação e migrations concluídas sem erro.
2. Container fica `healthy` pelo Dockerfile ou healthcheck equivalente do Coolify.
3. `GET https://<iam-domain>/actuator/health` retorna 200/`UP`; `GET https://<iam-domain>/.well-known/jwks.json` retorna apenas chaves **públicas**.
4. Frontend autorizado faz login real, CSRF, refresh e logout sob HTTPS, sem `HTTPS is required`; chamadas HTTP externas são redirecionadas/negadas.
5. Origens CORS e cookies `Secure`/`HttpOnly`/`SameSite` passam no navegador real.
6. SMTP de homologação envia convite/recuperação e as políticas de rate limit/revogação funcionam.
7. Logs não contêm credenciais/tokens; DNS, certificados, privilégios de banco, backups, rotação RSA e métricas/alertas foram revisados.

**Sem validação da confiança no proxy, marque deploy funcional de autenticação como NO-GO, mesmo que o healthcheck indique UP.**
