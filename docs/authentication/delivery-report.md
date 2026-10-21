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
| IIAMS-40 | Implementada, validação local | Nickname obrigatório, contato obrigatório compartilhável, Credential, IdentityAuthenticationApi/Adapter, contratos de cadastro | AuthenticationDomainTest; duplicidade, concorrência e resolução em AuthenticationIntegrationTest; regressão Identity | Nenhuma compatibilidade legada de Identity; nickname/e-mail obrigatórios, sem backfill |
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
- [x] IIAMS-50 — persistência Credential/nickname: V6/V7/V10, constraints e concurrentNicknameWritesHaveOneWinner.
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

V10 é a correção incremental da segunda rodada; **V1–V9 foram preservadas sem alterações de checksum**. Não houve acesso a ambiente persistente compartilhado para provar que V6–V9 nunca foram aplicadas; por isso não foram reescritas. Não há legado Identity a converter nem valores artificiais de nickname/contato. Hibernate continua validate. O teste de upgrade aplica V1–V5 em schema isolado, cria Session/Refresh legado e aplica as novas versões sem apagar o histórico ou inventar Identity proprietária. Banco vazio também é testado pela inicialização da suíte. Outro teste migra V9 com um job histórico de dez tentativas para V10: estado FAILED, contador preservado e nova solicitação permitida. Flyway validate confirma o histórico.

Relacionamentos operacionais de Credential/Challenge/IdentityRole não bloqueiam exclusão física; históricos e sessões preservam UUIDs como valores. Índices cobrem unicidade de nickname por kind, challenge por hash, sessões por proprietário/estado, tokens por sessão e jobs pendentes. Locking combina Identity/Credential/Session/Refresh; a descoberta inicial de token não povoa o persistence context antes de aguardar locks.

## E. Arquitetura

Identity permanece cadastral e publica contratos mínimos. Authentication orquestra credenciais, challenges e tokens, reutilizando Session/Refresh existentes. Authorization fornece catálogo e grants persistidos para o básico autorizado nesta conversa; não há motor contextual nem CRUD incompleto exposto. Platform controla HTTP, resource server, chaves, workers e bootstrap.

## F. Testes

Ver [validation.md](validation.md) para comando, total, resultados e limitações da execução final. Falhas encontradas durante desenvolvimento foram corrigidas: estado JPA obsoleto em refresh concorrente, flush antes de FK no bootstrap, exemplos OpenAPI sem nickname, isolamento do schema de teste de upgrade e tradução de negação @PreAuthorize para 403.

## G. Próximos passos

Authentication — nenhuma pendência funcional conhecida dentro das regras desta rodada após a validação registrada. Homologação: configurar HTTPS, origens, SMTP real, issuer/audience e chaves; ensaiar entrega e recuperação do primeiro operador. Operação futura: política de retenção. Não há implantação ou produção validada nesta entrega.

Authorization futuro: substituir a associação global básica por concessões PLATFORM/TENANT contextualizadas, decisão por recurso, gestão administrativa completa, auditoria de grants e revisão explícita da política ROOT. Não duplicar Permissions por nível. IIAMS-38 permanece fora desta rodada.

## H. GitHub

Um único Draft PR para main, referenciando IIAMS-39 e IIAMS-40–47. URL, HEAD SHA e checks finais serão informados na entrega e no próprio PR. Sem merge, deploy ou exclusão da branch.

## Segunda rodada — findings

A segunda rodada parte de `4a709174e98cb201dfbe49488ffe78b38f3aafc1`, mantém a mesma branch e atualiza somente o [Draft PR #2](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/pull/2). Epic, oito Stories, 32 subtarefas e IIAMS-80 foram relidos via Rovo Legacy. Nenhuma edição de Jira, Sprint, main, Gateway ou produção.

| Finding | Severidade | Issues | Correção aplicada | Arquivos principais | Testes comprobatórios | Estado |
|---|---|---|---|---|---|---|
| F-01 | Alta | IIAMS-40/41/47; 48/51/54/76 | E-mail obrigatório para todos, incluindo SERVICE e ROOT; recovery somente kind + nickname/documento | Identity, IdentityApplication, CreateServiceIdentityApplicationRequest, InitialIdentityProvisioningAdapter, V10 | missingOrInvalidEmailAndMissingNicknameAreRejectedForEveryKind; rootBootstrapWithoutEmailFailsWithoutIdentityMarkerOrOutput; recoveryWithSharedContactSelectsOnlyKindAndNickname | Corrigido e validado |
| F-02 | Alta | IIAMS-41/47; 55/77/78/79 | Cinco reservas/tentativas totais, backoff progressivo, FAILED terminal, nova solicitação independente, lock PostgreSQL durante SMTP e recuperação após queda | ChallengeDeliveryWorker, V10, operations.md | notificationStopsAtFiveFailuresAndAllowsNewJob; notificationBackoffIsProgressiveAndReservationSurvivesWorkerRestart; twoWorkersCannotSendSameJobEvenIfReservationDeadlineHasPassed; notificationPersistsHashesAndMetadataOnly | Corrigido e validado |
| F-03 | Descartada: compatibilidade sem legado | IIAMS-40/47; 50/78 | Não existe legado Identity. Removidos overloads sem nickname; construtores e SQL exigem nickname/contato. Sem backfill. Histórico de Session/Refresh preservado | Factories Identity/IdentityApplication, comandos, mappers, V10 | nicknameIsRequiredAtDomainConstruction; upgradesV5PreservingUnownedSessionHistory | Descartado como requisito de compatibilidade; simplificação aplicada |
| F-04 | Alta | IIAMS-41; 52/53/54/55 | Emissão/envio/falha não invalida links anteriores. Consumo invalida siblings somente da mesma Identity/finalidade; senha, estado e revogação na mesma transação | AuthenticationUseCases, AuthenticationStore, JdbcAuthenticationStore | newerLinkNeverInvalidatesOlderAndSuccessfulConsumptionInvalidatesSiblings; newestLinkMayWinAndFailedDeliveryLeavesOlderLinkUsable; failedSmtpAttemptLeavesPreviouslyDeliveredLinkConsumable; distinctConcurrentLinksHaveExactlyOneWinner; completeRollbackPreservesBothLinksAndCredential; completingResetPreservesChallengesOfOtherPurposes | Corrigido e validado para ativação e reset |
| F-05 | Média/alta | IIAMS-41/47; 53/77 | ADMIN_INVITATION registra ator, alvo, ação, UTC, resultado e origem da conexão. Sem justificativa obrigatória ou segredos. Sem infraestrutura prévia de correlation ID; nenhum header artificial adotado | AuthenticationController, AuthenticationUseCases, JdbcAuthenticationStore, V10 | administrativeInvitationAuditsActorTargetAndTrustedSocketOrigin; unauthorizedAdminCannotIssueInvitationAndPublicRequestHasDistinctEvent; forwardedHeadersCannotSpoofRecordedAdministrativeOrigin | Corrigido e validado |
| F-06 | Alta | IIAMS-45; 68/71 | Uma operação transacional compara Identity e SID de Bearer/cookie antes de qualquer efeito. Mismatch 401 com auditoria, sem revogar/consumir. Idempotência cookie-only | AuthenticationController, AuthenticationUseCases | mismatchedLogoutIsUnauthorizedAndHasNoEffects; validCombinedAccessOnlyCookieOnlyAndRepeatedCookieLogout; invalidCookieCannotRevokeBearerSessionAndFailureRollsBackLogout; expiredBearerIsRejectedAndClientCanLogoutByOmittingIt; databaseFailureDuringTokenRevocationRollsBackSessionLogout | Corrigido e validado |
| F-07 | Integração futura | IIAMS-80 | IIAM standalone; forwarded headers não confiáveis ignorados, HTTPS exigido por padrão. Nenhum arquivo do Gateway alterado; integração/proxy/TLS futuros não implementados | application.yaml, operations.md, web-and-jwt.md | forwardedHeadersCannotSpoofRecordedAdministrativeOrigin; regressão de HTTP/CSRF/CORS | Documentado em IIAMS-80; Gateway não corrigido nesta rodada |
| F-08 | Alta | IIAMS-40/47; 48/50/51/76 | E-mail sem unicidade em qualquer kind; removidos queries/checks/locks/índices exclusivos de e-mail. CPF/CNPJ e nickname por kind continuam únicos | IdentityApplicationConflicts, repositórios/JPA/adapters Identity, PostgresIdentityRegistrationLock, V10 | sharedEmailAllowsTwoRegistrationsAndDocumentNicknameUniquenessRemains (3 kinds); emailUpdateMayReuseAnotherIdentityContact; sameEmailIsAllowedAcrossKindsAndRejectionReleasesPendingKey; duplicatesAreBlockedBothBeforeAndAfterApplication; concurrentNicknameWritesHaveOneWinner | Corrigido e validado |
| F-09 | Média (avaliação técnica) | IIAMS-40/46/47; 59/74/75/78 | Removidas propriedades nickname duplicadas, adicionados nicknames às respostas, parser estrito e schemas com contato obrigatório. Descrições de cadastro/approval corrigidas | IdentityApiExamples, IdentityResponse, IdentityApplicationController, AdminIdentityApplicationController, CreateServiceIdentityApplicationRequest | rawExamplesAreStrictJsonAndEveryKindRequiresContactAndNickname; IdentityOpenApiIntegrationTest | Corrigido e validado |

Todos os testes da matriz estão em AuthenticationStabilizationIntegrationTest, salvo os de regressão nominados e IdentityOpenApiIntegrationTest/MigrationUpgradeIntegrationTest. Não se considera inspeção ou compilação isolada como prova de atendimento.

### Banco desta rodada

Migration acrescentada: `V10__authentication_stabilization.sql`. NOT NULL para nickname e contato de Identity/Application; check de nickname normalizado em Application; remoção dos índices `uk_identity_kind_email_ci` e `uk_application_pending_kind_email`; auditoria ganha actor_identity_id, target_identity_id, result e request_origin, com índice ator/alvo/UTC; fila ganha state e finished_at, checks de estado/contador e índices parciais que reservam apenas PENDING/PROCESSING. Registros terminais e histórico são mantidos. Nenhum segredo é inserido em jobs ou auditoria.

Não existe constraint que limite a uma challenge válida por finalidade. Os links coexistem por hash e têm expiração própria; o lock na Identity serializa consumos de links diferentes, incluindo reset quando o estado continua ACTIVE. ConsumePurpose e revogação de sessões são separados da revogação global usada por disable/change password/outbox. Não há novo User ou duplicação de cadastro pessoal.

### Evidência e limites

Ver [validation.md](validation.md) e [test-results.json](test-results.json) para total final extraído do Surefire, SHA da árvore validada, comandos e CI. A suíte anterior de 154 casos foi mantida, adaptando o cenário SERVICE sem contato à nova regra e os fixtures para nickname explícito. Foram acrescentados testes de regressão dos findings, PostgreSQL, HTTP e OpenAPI.

Authentication não depende do Gateway ou de consumidor externo para executar. JWT RS256 permanece 900s; Session/refresh continuam sete dias absolutos, sem encurtar o access emitido próximo ao limite. Authorization básico/ROOT existente foi preservado. RBAC PLATFORM/TENANT, gestão administrativa completa de grants, IIAMS-38, SSO/OIDC e Gateway IIAMS-80 são evolução futura; HTTPS/SMTP/chaves/origens reais são homologação, não implementação pendente dos findings.
