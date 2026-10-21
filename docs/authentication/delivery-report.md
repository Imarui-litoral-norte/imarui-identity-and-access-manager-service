# Relatório de entrega — IIAMS-39

## Referências, alcance e situação real

Origem Jeep Club master `9ad04a71e50917e0a53c1c4855ea6770730bd8b9`; destino IIAM main `687fb0a1a9dabed772dba26a08e14fcd9c6ee684`. O nome antigo do remoto redireciona para o mesmo repositório GitHub. A branch de trabalho é `feat/iiams-39-authentication-jeep-migration`.

Requisitos obtidos no Rovo Legacy, incluindo descrições completas das oito Stories e 32 subtarefas e suas regras definitivas. Não houve alteração de status no Jira. A entrega é código e evidência local/CI em Draft PR; não houve merge, deploy, acesso ou alteração de banco de produção.

O bootstrap ROOT ativo é uma alteração explícita posterior do usuário ao limite inicial do pedido. Usa Permission/Role/RolePermission/IdentityRole persistidos, catálogo IIAM e verificação ACTIVE. O cadastro administrativo contextual do futuro Authorization continua fora do escopo.

## A. Reaproveitamento efetivo

| Grupo | Resultado efetivo |
|---|---|
| Permission, RolePermission e exceptions de Permission | Código do Jeep adaptado em packages e catálogo; testes de sincronização/invariantes |
| Role e UserRole | Modelos mínimos adaptados a Role/IdentityRole UUID; lifecycle administrativo completo não importado |
| AuthenticationAccount | Conceitos adaptados para Credential: hash, falhas, bloqueio temporário, habilitação e timestamps; sem dados cadastrais |
| AccountActivationInvitation, PasswordChangeChallenge, PasswordRecoveryRequest | Invariantes consolidadas em AuthenticationChallenge com finalidade, hash, expiração e consumo único |
| Session, RefreshToken e adapters IIAM | Preservados e evoluídos, com IdentityId, SID, revogação, prazo absoluto e rotação |
| SessionService/TokenIssuanceService/RefreshTokenService Jeep | Orquestração adaptada aos casos de uso IIAM; corrigida descoberta de refresh sem estado JPA obsoleto antes do lock |
| CredentialRevocationService | Adaptado para contratos de Identity e consumidor de outbox transacional/idempotente |
| JWT/parsing/segurança Jeep | HS256 e principal Long descartados; RS256, claims UUID, JWKS e resource server IIAM integrados |
| ROOT e authorities | Bootstrap e sincronização adaptados por autorização posterior; authorities reais consultadas em persistência |
| Memberships, Billing, exports e administração Authorization incompleta | Não importados; incompatibilidades/adiamentos registrados na matriz |
| Notificação Dummy Jeep | Removida; SMTP real com fila de metadados e retry sem persistência do segredo |

A [matriz por componente de origem](reuse-matrix.md) separa a decisão inicial da auditoria do resultado efetivo. Não se presume que todo arquivo inventariado foi copiado.

## B. Stories

| Story | Status técnico | Implementação e principais classes | Testes/evidência | Pendências |
|---|---|---|---|---|
| IIAMS-40 | Implementada, validação local | Nickname, Credential, IdentityAuthenticationApi/Adapter, contratos de cadastro | AuthenticationDomainTest; duplicidade, concorrência e resolução em AuthenticationIntegrationTest; regressão Identity | Regularizar nickname de eventuais dados legados antes de autenticar |
| IIAMS-41 | Implementada, validação local | AuthenticationChallenge, AuthenticationUseCases, ChallengeDeliveryWorker, SmtpChallengeMail | Primeiro acesso nos três kinds, expiração, consumo paralelo, reset, retry e SMTP local real | Validar provedor SMTP e entrega em homologação |
| IIAMS-42 | Implementada, validação local | Login, RsaAccessTokenIssuer, JwtSigningKeyProvider, JWKS | Claims/900s, CPF/CNPJ/nickname, e-mail negado, JWTs inválidos, fluxo HTTP para três kinds | Configurar emissor/audience/chaves do ambiente |
| IIAMS-43 | Implementada, validação local | Session/SessionMapper, sessões próprias e administração preservada/paginada | Isolamento, SID, sete dias e APIs administrativas | Política operacional de retenção |
| IIAMS-44 | Implementada, validação local | Refresh com locks, linhagem, replay e cookie seguro | Concorrência real, rotação HTTP, expiração absoluta, CSRF/CORS e ausência de refresh no JSON | Ensaio de navegador com HTTPS/topologia final |
| IIAMS-45 | Implementada, validação local | Logout, RevocationRequests, RevocationProcessor e cutoff | Idempotência, disable/reactivate, rollback/retry, contexto Tenant e janela offline | Operação/monitoramento da fila no ambiente |
| IIAMS-46 | Implementada conforme reescopo V1 | RS256/JWKS, cadeia HTTP e contrato futuro de Actor | JWT, issuer/audience, chave errada, rotação, 401/403 e claims de permission sem concessão | Consumidores externos/Actor compartilhado são evolução futura |
| IIAMS-47 | Implementada; validação remota registrada no PR | AuthenticationBootstrap, catálogo ROOT, rate limiting, auditoria, CI e runbooks | Bootstrap repetido, permissions persistidas, throttle, regressão e build PostgreSQL | HTTPS/SMTP/chaves reais e revisão humana antes de implantação |

As pendências operacionais acima não foram executadas contra ambientes reais e não são apresentadas como homologação ou deploy concluídos. O estado dos checks remotos e o HEAD estão no PR; a evidência local está em [validation.md](validation.md).

## C. Checklist individual de subtarefas

Cada marca indica implementação com evidência local ou documentação exigida pelo reescopo; não corresponde a transição no Jira.

- [x] IIAMS-48 — contrato kind/nickname/CPF/CNPJ, ambiguidades e e-mail proibido: README, web-and-jwt e AuthenticationDomainTest.
- [x] IIAMS-49 — Credential, BCrypt, política de senha/bloqueio: AuthenticationDomainTest e credentialFailuresPersistAndBlockAtFive.
- [x] IIAMS-50 — persistência Credential/nickname: V6/V7, constraints e concurrentNicknameWritesHaveOneWinner.
- [x] IIAMS-51 — consulta/lock e elegibilidade Identity: IdentityAuthenticationApi e allKindsFirstAccessLoginJwtRotationAndLogout.
- [x] IIAMS-52 — challenge/hash/finalidade/validade: expiredChallengeAndWeakPasswordLeaveIdentityPending e parallelChallengeConsumptionIsAtomic.
- [x] IIAMS-53 — primeiro acesso atômico: completeBrowserFlowForEveryKind, activationFailureRollsBackCredentialChallengeAndIdentity, prova exigida pela ativação antiga e regressão IdentityEndToEndTest.
- [x] IIAMS-54 — recuperação/troca/revogação: resetRevokesExistingSessionsAndCannotBeReused e authenticatedPasswordChangeRequiresCurrentPasswordAndRevokesSession.
- [x] IIAMS-55 — notificações, links e retry: SmtpChallengeMailTest (SMTP local real), notificationJobRetriesWithoutStoringRawSecret; SMTP externo pendente de ambiente.
- [x] IIAMS-56 — login determinístico e estados: allKindsFirstAccessLoginJwtRotationAndLogout, bloqueio e e-mail negado.
- [x] IIAMS-57 — JWT/JWKS assimétricos: jwtRejectsWrongIssuerAudienceSignatureAndExpiration e JwtKeyRotationTest.
- [x] IIAMS-58 — audience mínima configurável: JwtProperties, validator e teste de audience errada; sem client registry.
- [x] IIAMS-59 — HTTP login/contrato: completeBrowserFlowForEveryKind e webCookieCsrfAndNoRefreshInJson.
- [x] IIAMS-60 — Session Identity/SID: V8, mappers e validação de claims/proprietário.
- [x] IIAMS-61 — sessões próprias: foreignSessionCannotBeClosed, consultas por titular e encerramento com revogação.
- [x] IIAMS-62 — TTL absoluto: absoluteSessionExpiryCannotBeExtended e invariantes 900s/7d dos três kinds.
- [x] IIAMS-63 — APIs administrativas: bootstrapIsOneShotAndRootHasRealAuthorities, permissionClaimsDoNotGrantAdministrativeAccess e paginação preservando rotas/response list.
- [x] IIAMS-64 — refresh elegível: fluxo HTTP nos três kinds e rejeição após logout/disable/expiração.
- [x] IIAMS-65 — rotação/replay atômicos: parallelRefreshProducesOneSuccessAndRevokesReplay, locks PostgreSQL e linhagem.
- [x] IIAMS-66 — cookies/CSRF/CORS: webCookieCsrfAndNoRefreshInJson, completeBrowserFlowForEveryKind e corsAllowlistAndGenericRecoveryThrottleAreEnforced.
- [x] IIAMS-67 — segurança e compatibilidade refresh: expiração absoluta, replay, ausência de hash/token em DTO e administração preservada.
- [x] IIAMS-68 — logout direto/idempotente: allKindsFirstAccessLoginJwtRotationAndLogout e fluxo HTTP, cookie Max-Age=0.
- [x] IIAMS-69 — outbox transacional: outboxFailureRollsBackEffectsAndRetrySucceeds e identityDisableOutboxAndReactivationDoNotResurrectTokens.
- [x] IIAMS-70 — contexto Tenant: IdentityRevocationIntegrationTest e regressão Affiliation; corte contextual sem revogar sessão global.
- [x] IIAMS-71 — revogação e JWT offline: JWT ainda verificável criptograficamente após logout, IIAM rejeita sessão encerrada; testes de cutoff/rollback/retry.
- [x] IIAMS-72 — contrato JWT/JWKS: web-and-jwt e JwtKeyRotationTest; sem implantação de consumidor externo.
- [x] IIAMS-73 — Actor futuro documentado: exemplo de extração de sub/sid após verificação, sem SDK/objeto persistido/serviço externo.
- [x] IIAMS-74 — HTTP security: allowlist pública, CSRF/CORS, 401/403 RFC9457 e regressão dos controllers Identity.
- [x] IIAMS-75 — guia frontend: sequência login/refresh/logout, cookies e recuperação de conflitos em web-and-jwt.
- [x] IIAMS-76 — bootstrap: bootstrapIsOneShotAndRootHasRealAuthorities; PENDING/convite/ACTIVE e arquivo protegido sem senha fixa.
- [x] IIAMS-77 — rate limit/auditoria: throttle PostgreSQL, bloqueio persistido, respostas genéricas e remoção do logger de links legados.
- [x] IIAMS-78 — CI/build/migrations/E2E: verify.yml, suíte real PostgreSQL e upgradesV5PreservingUnownedSessionHistory.
- [x] IIAMS-79 — runbooks: operations.md, configuração, chaves, notificações, revogação, retenção e limites da V1.

## D. Banco

V6–V9 são incrementais; V1–V5 foram preservadas. Hibernate continua validate. O teste de upgrade aplica V1–V5 em schema isolado, cria Session/Refresh legado e aplica as novas versões sem apagar o histórico ou inventar Identity proprietária. Banco vazio também é testado pela inicialização da suíte.

Relacionamentos operacionais de Credential/Challenge/IdentityRole não bloqueiam exclusão física; históricos e sessões preservam UUIDs como valores. Índices cobrem unicidade de nickname por kind, challenge por hash, sessões por proprietário/estado, tokens por sessão e jobs pendentes. Locking combina Identity/Credential/Session/Refresh; a descoberta inicial de token não povoa o persistence context antes de aguardar locks.

## E. Arquitetura

Identity permanece cadastral e publica contratos mínimos. Authentication orquestra credenciais, challenges e tokens, reutilizando Session/Refresh existentes. Authorization fornece catálogo e grants persistidos para o básico autorizado nesta conversa; não há motor contextual nem CRUD incompleto exposto. Platform controla HTTP, resource server, chaves, workers e bootstrap.

## F. Testes

Ver [validation.md](validation.md) para comando, total, resultados e limitações da execução final. Falhas encontradas durante desenvolvimento foram corrigidas: estado JPA obsoleto em refresh concorrente, flush antes de FK no bootstrap, exemplos OpenAPI sem nickname, isolamento do schema de teste de upgrade e tradução de negação @PreAuthorize para 403.

## G. Próximos passos

Authentication: configurar/homologar HTTPS, origens, SMTP, issuer/audience e gestão de chaves no ambiente; definir retenção operacional e ensaiar recuperação do primeiro operador. Não há implantação ou produção validada nesta entrega.

Authorization futuro: substituir a associação global básica por concessões PLATFORM/TENANT contextualizadas, decisão por recurso, gestão administrativa completa, auditoria de grants e revisão explícita da política ROOT. Não duplicar Permissions por nível. IIAMS-38 permanece fora desta rodada.

## H. GitHub

Um único Draft PR para main, referenciando IIAMS-39 e IIAMS-40–47. URL, HEAD SHA e checks finais serão informados na entrega e no próprio PR. Sem merge, deploy ou exclusão da branch.
