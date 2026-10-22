# IIAM Authorization — relatório final da Rodada 2

Data: 2026-10-22. Epic [IIAMS-81](https://logimarui.atlassian.net/browse/IIAMS-81).
Branch: `feat/iiams-81-authorization-foundation`; base `main`.
PR consolidado: [#3](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/pull/3).
Continuidade do commit revisado `ef10547909aa9ee066957e649c1af6b1351ad34f`, preservando
os seis commits da Rodada 1. Não houve merge, deploy ou alteração do Gateway.

## 1. Cinco Stories e 27 subtarefas desta rodada

| Story | Subtarefas | Entrega técnica | Evidência principal |
| --- | --- | --- | --- |
| IIAMS-88 | 124–129 (6) | PDP central, batch, credencial M2M única para decisão/catálogo, PEP real e fail-closed | ServiceIntegration + PepClient; guia do consumidor |
| IIAMS-89 | 130–134 (5) | Envelope ROOT, último ACTIVE, transferência com dupla reautenticação, constraints e runbook | RootRecoveryIntegration + AdministrationIntegration |
| IIAMS-90 | 135–139 (5) | APIs de Scope/Role/lifecycle/composição/assignment/DENY/delegação/catálogo, disponibilidade, explicação e paginação | AdminHttpIntegration + ServiceIntegration + OpenAPI |
| IIAMS-91 | 140–144 (5) | Auditoria transacional, revisão consistente, revogação sem login, métricas e logs sem segredos | Administração/Service/Root/Audit; benchmark |
| IIAMS-92 | 145–150 (6) | Migrations adicionais, upgrades povoados, regressão completa, contratos e guias operacionais | UpgradeIntegration + clean verify + CI |

As descrições do Epic foram consultadas uma vez pelo **Atlassian Rovo Legacy** do logimarui.
Stories/subtarefas foram recuperadas por pequenos lotes e preservadas em [jira](jira/), sem dados
de conta. Foram conferidas as 11 Stories e 58 subtarefas. A indisponibilidade Jira da Rodada 1
não persistiu; não foi necessário inventar critérios nem repetir a auditoria integral do Jeep Club.
A matriz da seção 13 corresponde aos títulos reais recuperados. Isto é evidência técnica,
não aceite humano, alteração de Sprint, aprovação de PR ou autorização para produção.

## 2. Revalidação das seis Stories da fundação

| Story | Resultado da revalidação |
| --- | --- |
| IIAMS-82 | UUID/código estáveis, múltiplas raízes, integridade/ciclos, reparenting, Tenant→Scope, histórico e rollback preservados |
| IIAMS-83 | Owner/namespace/versionamento/depreciação/IDs e histórico preservados; adapter M2M agora operacional, sem autogrants |
| IIAMS-84 | Ownership GLOBAL/SCOPE/TENANT preservado; lifecycle explícito ACTIVE/INACTIVE/ARCHIVED e exclusão histórica concluídos |
| IIAMS-85 | Assignments continuam independentes de Affiliation, com herança, revogação, owner e versão; criação exige Role ACTIVE |
| IIAMS-86 | DENY absoluto e anti-bypass preservados; unicidade lógica/persistente, idempotência e reconciliação implementadas |
| IIAMS-87 | Delegação granular, redelegação explícita, self-grants e impacto de Roles compartilhadas/reparenting preservados |

Os 262 testes anteriores foram novamente executados no início da rodada. A regressão final
inclui os testes anteriores e os novos cenários; o resultado inicial não foi utilizado como
substituto da validação atual. [Handoff histórico](ROUND_1_HANDOFF.md),
[requisitos da Rodada 1](ROUND_1_REQUIREMENTS.md) e [auditoria legada](legacy-audit.md) foram lidos.

## 3. Findings F-03, F-04 e F-05

**F-03 resolvido:** `DenyRule.sameTarget`, criação idempotente sob lock, índices únicos parciais
por Identity/Role em V18. Duplicatas legadas são reconciliadas preservando o ativo mais antigo,
inativando os demais e registrando before/after. Revogação é versionada; recriação após revogação
gera novo ID, não ressuscita registro histórico. Há testes de repetição, destinatários separados,
SQL concorrente/constraint, concorrência de operações e upgrade V17 populado.

**F-04 resolvido:** Role.Status explícito; boolean antigo migrado sem perda e mantido como projeção
física de compatibilidade. Mappers, evaluator, persistência, DTOs e consultas usam status.
INACTIVE não concede e retém vínculos; ARCHIVED exige ausência de assignment ativo e é terminal.
Exclusão física somente de ARCHIVED sem qualquer assignment operacional, inclusive revogado,
preservando `authorization_roles__history`. ROOT não pode seguir o CRUD comum. V19/V24,
testes de lifecycle/assignments/ownership/impacto e história independente comprovam as regras.

**F-05 resolvido:** `TechnicalServiceAuthentication` em Authentication e seu adapter JDBC
autenticam catálogo e PDP pelo mesmo protocolo. `AuthenticatedCatalogServiceAdapter` vincula
o caller ao owner, à operação CATALOG_SYNC e à revisão atual da credencial. Revalidação ocorre
após o lock do catálogo. Há HTTP real de sincronização idempotente, owner alheio, versão conflitante,
credencial inválida/expirada/revogada, rotação, replay e política alterada após a prova.

## 4. Avaliação de F-02

Mantidos snapshot amplo e lock transacional 810082. Foi corrigida reconstrução redundante de árvore
nos loops de impacto; a árvore só é reutilizada dentro da mesma revisão, sem cache entre chamadas.
Uma transferência ROOT que inicialmente excedia 10 s passou no checkpoint em suíte de 8 casos
com 3,196 s após essa correção. Não se enfraqueceu a checagem para passar o teste.

[Medição isolada registrada](f02-benchmark.json), PostgreSQL 18.4/Java 17.0.18:

| Dimensão | Valor |
| --- | --- |
| Scopes / Identities / Roles / assignments / Permissions | 2.365 / 301 / 41 / 901 / 172 |
| Snapshot, 30 amostras | p50 10,8272 ms; p95 12,4119 ms; máximo 15,8572 ms |
| Árvore, 30 amostras | p95 0,5342 ms |
| Avaliação com árvore pronta, 100 amostras | p95 0,0035 ms |
| Uma edição de Role compartilhada | 887,035 ms |

O cenário adicional contém 300 Identities, 60 Scopes, 40 Roles e 900 assignments; os totais
incluem fixtures existentes no banco isolado. Não mede capacidade/SLA de produção, contenção
em cluster ou universo ilimitado. Novas execuções geram artefato próprio em target; o JSON
versionado registra esse checkpoint específico. **Ressalva remanescente baixa:** monitorar
crescimento e serialização de escritas antes de otimizar consultas/cache, com teste de revogação.

## 5. Arquitetura final

Ver [arquitetura detalhada](ARCHITECTURE.md). Domínio/evaluator da fundação foram continuados,
sem um segundo algoritmo nos controllers. Identity mantém seus dados e publica estado atual;
Authentication valida sessões/revogações e autenticação técnica; Authorization decide e governa
políticas; Platform compõe HTTP/JWT/OpenAPI. Consumidor possui e aplica a operação no recurso.
Permissões de JWT antigas não concedem autorização e sessão não tem active Scope obrigatório.

Leitura REPEATABLE READ usa uma revisão consistente; mutações revalidam autoridade/versão
depois do lock. A decisão pode terminar na revisão que começou antes de uma revogação concorrente;
as próximas decisões após commit enxergam a mudança. Não há cache de ALLOW entre operações.
Não se promete atomicidade distribuída entre o banco do consumidor e o IIAM.

## 6. APIs e contratos

Contrato completo: [OpenAPI exportado](openapi.json), também `/v3/api-docs` e `/docs` no aplicativo.
O arquivo é obtido do HTTP real pela suíte; DTOs explícitos, lifecycle, WRITE_ONLY para tokens/senhas,
seguranças bearer humana/serviceProof e erros ProblemDetail estão documentados.

| Grupo | Prefixo `/api/v1/authorization` | Controle |
| --- | --- | --- |
| Scopes, reparenting e histórico seguro | `/admin/scopes` | SCOPE_MANAGE, owner/hierarquia, versão, recuperação |
| Roles, lifecycle e composição | `/admin/roles` | ROLE_MANAGE no proprietário + delegação/impacto |
| Assignments / DENYs / delegações | `/admin/assignments`, `/denies`, `/delegations` sob `/admin` | Permission própria, Scope, granularidade, versão e anti-self-grant |
| Catálogo e primeira autoridade | `/admin/catalog` | CATALOG_MANAGE; concessão inicial central explícita |
| Roles disponíveis e permissões efetivas | `/admin/roles/available`, `/admin/tenants/{id}/roles/available`, `/admin/effective-permissions` | AUDIT_READ na fronteira; disponibilidade não concede gestão |
| Diagnóstico e auditoria | `/admin/decisions/explain`, `/admin/audit` | AUDIT_READ por Scope; eventos globais somente PLATFORM |
| Clientes técnicos e chaves | `/admin/service-clients` | SERVICE_MANAGE central + SCOPE_MANAGE das jurisdições; inspeção por auditoria central |
| Transferência ROOT | `/admin/root/transfers` | ROOT existente na fronteira, duas sessões/reautenticações, validade e confirmação |
| PDP / batch / catálogo externo | `/service/decisions`, `/service/decisions/batch`, `/service/catalog` | Prova M2M + operação; PDP também exige sessão humana e ownership |

Listagens têm page/size com limite 100 e filtros por fronteira; alterações de existentes exigem
versão explícita, testada sob lock. HTTP distingue 400/401/403/404/409/413/415/429/503. Resposta
comum de decisão omite proveniência; explicação administrativa distingue ALLOW/DENY direto/herdado,
Role e Scope de origem e default deny. Administrador local não lê/edita indiscriminadamente owner
ancestral ou Tenant irmão. Actuator mantém health público e exige auditoria central nos demais endpoints.

## 7. Modelo M2M

Client UUID técnico separado da Identity humana, código proprietário, operações e raízes permitidas.
Prova RS256 por requisição, issuer/subject vinculados à chave cadastrada, audience distinta,
validade até 60 s, nonce persistido de uso único e binding método/path/corpo/correlação.
Chaves públicas RSA 2048–4096, validade até 90 dias, fingerprints, rotação sobreposta/revogação.
Não se armazena chave privada técnica no IIAM. Não se trata o esquema próprio como protocolo OAuth.

PDP revalida autorização técnica dentro do snapshot corrente, JWT/sessão humana/Identity e Actor;
Scope vem do binding Tenant oficial ou objeto previamente aprovado. Caller não pode trocar o Actor
do JWT nem consultar Permission alheia ao serviço. Limites distribuídos, 64 KiB, UTF-8, batch ≤20,
timeouts e referência PEP sem fallback/cache/redirect completam a superfície.
Consultar [guia de integração](CONSUMER_GUIDE.md) e testes contratuais reais.

## 8. ROOT e recuperação administrativa

Envelope mínimo explícito de nove capacidades de segurança; DENY comum permanece absoluto.
Proteção de último ACTIVE em aplicação e PostgreSQL, inclusive alterações concorrentes e
tentativa de substituir administrador por PENDING. Nenhum CRUD genérico cria/clona ROOT.
Transferência exige iniciador ROOT, destinatário ACTIVE distinto, motivo/correlação, duas
reautenticações e sessões atuais, confirmação em 10 minutos e limite de três pendências.
Assignment/delegação anterior são revogados na mesma transação; restrições de negócio que
seriam perdidas são preservadas explicitamente. Raiz independente não ganha PLATFORM.

[Runbook](OPERATIONS.md) define fluxo, investigação e limites de emergência. Recuperação de senha
usa Authentication. Catástrofe de banco/chaves exige backup/restauração autorizados; não há endpoint
anônimo ou SQL de concessão irrestrita. O ensaio não realizou recuperação de infraestrutura de produção.

## 9. Migrations e preservação de dados

Somente V18–V24 adicionadas nesta rodada; V1–V17 permanecem intocadas. V11–V17 da Rodada 1
continuam no PR. [Operação](OPERATIONS.md) detalha cada versão e preflight.
Testes: instalação limpa; V10 populado com IDs, grants, Tenant, Identity, sessões e refresh;
V17 com duplicatas DENY e Role inativa; ROOT legado com ID/assignment e nove delegações;
V20 populado com client/chave revogada, nonce, jurisdição e evento de segurança.

Históricos não são eliminados para corrigir falhas. Rollback testado é transacional de operações
e migrations PostgreSQL, não down migration destrutiva nem restauração real de backup.
Reversão operacional deve ser progressiva ou restaurar backup ensaiado; não editar Flyway aplicado.

## 10. Auditoria, consistência e observabilidade

Catálogo/owner/manifest, Scope, Role/composição, assignment, DENY, delegação, ROOT, client/chave,
PDP e recuperação possuem eventos. Before/after administrativo e eventos técnicos registram
atores, serviço, recurso, Scope, instante, motivo e correlação, sem segredos. Auditoria e histórico
são append-only; exclusão operacional preserva trilha independente. Auditoria de Scope apagado
é consultável pelo ancestral autorizado; eventos globais exigem auditoria central.

ALLOW depende da gravação do evento de decisão com mesmo decisionId na mesma transação.
Falha de auditoria não libera recurso. Negativas são registradas após rollback; logs e contadores
de decisão só confirmam commit. Versões/mudanças de Role, DENY, delegation, Scope, Identity,
Tenant, sessão e credencial técnica afetam novas decisões sem novo JWT/login.
Métricas e limites estão no runbook; não foi instalado exportador externo nem criado cache.

## 11. Testes e evidências atuais

**Regressão local final:** 320 testes, 0 falhas, 0 erros, 0 ignorados; `mvnw.cmd -B clean verify`.
Java 17.0.18, PostgreSQL 18.4 isolado, JAR Spring Boot gerado. Resultado estruturado e suites:
[round-2-test-results.json](round-2-test-results.json). O resultado Round1 de 262 é histórico.
**CI do código final:** [Verify IIAM aprovado](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/actions/runs/39872457050), commit de código `88354397b595c39d6c92c1b1cb739c5231b37410`, 320 testes, 0 falhas/erros/ignorados, PostgreSQL 17.11. A consolidação documental posterior não altera o código validado. O workflow usa PostgreSQL 17 e Java 17,
publica Surefire, OpenAPI e benchmark. Não confundir check legado vazio com sucesso de CI.

Cobertura inclui Identity, Authentication, HTTP, JWT/JWKS, sessão/refresh/logout/revogação,
upgrade, ciclo de Role, self-grants, ownership, DENY absoluto, delegação, ROOT concorrente,
rollback parcial, M2M inválido/rotacionado/revogado/replay concorrente, spoof Actor/Scope,
isolamento entre Tenants, PDP concorrente, batch, falha de auditoria, mesmo JWT após mudanças,
PEP real e timeout/integração inválida. O benchmark é sintético, não teste de capacidade de produção.

## 12. Findings remanescentes e classificação

| Item | Estado | Impacto/limite |
| --- | --- | --- |
| F-03 / F-04 / F-05 | Implementado e testado | Unicidade/lifecycle/M2M resolvidos; evidências acima |
| F-02 | Implementado com ressalvas de escala | Consistência preservada e custo medido; serialização/escan amplo precisam monitoração com crescimento |
| F-01 histórico: Jira indisponível | Resolvido nesta rodada | 11 Stories e 58 subtarefas recuperadas; sem afirmação de aceite humano |
| Recuperação de desastre | Procedimento documentado; infraestrutura não ensaiada em produção | Não há bypass; restauração exige operação autorizada e teste de backup fora desta execução |
| Gateway IIAMS-80 | Não implementado por limite explícito | Nenhum arquivo/repositório do Gateway alterado; habilitação de aplicações por Tenant permanece futura |
| Critérios não verificáveis por Jira | Nenhum nesta rodada | Snapshots de requisitos recuperados em jira/ |

Nenhum finding estrutural de autorização, integridade ou exposição administrativa ficou
deliberadamente aberto. As ressalvas acima são limites explícitos da evidência e operação,
não autorização para deploy nem afirmação de capacidade ilimitada.

## 13. Evidências por subtarefa — 58 de 58

Cada linha cruza descrição Jira recuperada com implementação e testes executáveis. Os links
para fontes e suites são relativos ao mesmo commit. Status **IT** significa implementado e
testado; **IR** significa implementado com ressalva explicitada. Não há item obrigatório marcado
não implementado ou não verificável; Gateway é exclusão expressa. Artefatos documentais são
validados por seus contratos/testes associados, sem alegação de execução de deploy/backup real.

### IIAMS-82

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-93](https://logimarui.atlassian.net/browse/IIAMS-93) — Definir domínio AuthorizationScope e invariantes de árvore | IT | [AuthorizationScope][AuthorizationScope] | [ScopeHierarchyTest][ScopeHierarchyTest] |
| [IIAMS-94](https://logimarui.atlassian.net/browse/IIAMS-94) — Persistir Scopes e consultas hierárquicas eficientes | IR — limites nas seções 4/8/12 | [JdbcScopeRepository][JdbcScopeRepository] | [ScopePersistenceIntegrationTest][ScopePersistenceIntegrationTest], [AuthorizationPerformanceIntegrationTest][AuthorizationPerformanceIntegrationTest] |
| [IIAMS-95](https://logimarui.atlassian.net/browse/IIAMS-95) — Vincular Tenant ao Scope e criar bootstrap Imaruí | IT | [JdbcScopeRepository][JdbcScopeRepository] | [ScopePersistenceIntegrationTest][ScopePersistenceIntegrationTest], [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest] |
| [IIAMS-96](https://logimarui.atlassian.net/browse/IIAMS-96) — Implementar gestão segura de hierarquia e reparenting | IT | [ScopeAdministrationUseCases][ScopeAdministrationUseCases] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-97](https://logimarui.atlassian.net/browse/IIAMS-97) — Testar herança, estabilidade de UUID e fronteira do Identity | IT | [ScopeHierarchy][ScopeHierarchy] | [ScopeHierarchyTest][ScopeHierarchyTest], [ScopePersistenceIntegrationTest][ScopePersistenceIntegrationTest] |

### IIAMS-83

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-98](https://logimarui.atlassian.net/browse/IIAMS-98) — Modelar metadados e ciclo de vida do Permission Catalog | IT | [Permission][Permission] | [PermissionCatalogIntegrationTest][PermissionCatalogIntegrationTest] |
| [IIAMS-99](https://logimarui.atlassian.net/browse/IIAMS-99) — Definir manifesto versionado e protocolo federado de sincronização | IT | [PermissionManifest][PermissionManifest] | [PermissionManifestTest][PermissionManifestTest], [PermissionCatalogIntegrationTest][PermissionCatalogIntegrationTest] |
| [IIAMS-100](https://logimarui.atlassian.net/browse/IIAMS-100) — Proteger registro de Permission com credenciais M2M e ownership | IT | [AuthenticatedCatalogServiceAdapter][AuthenticatedCatalogServiceAdapter] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |
| [IIAMS-101](https://logimarui.atlassian.net/browse/IIAMS-101) — Integrar Permissions internas de Authentication/Authorization/Identity | IT | [PermissionCatalogBootstrap][PermissionCatalogBootstrap] | [PermissionCatalogIntegrationTest][PermissionCatalogIntegrationTest], [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest] |
| [IIAMS-102](https://logimarui.atlassian.net/browse/IIAMS-102) — Implementar depreciação, reconciliação e histórico do catálogo | IT | [PermissionCatalogUseCases][PermissionCatalogUseCases] | [PermissionCatalogIntegrationTest][PermissionCatalogIntegrationTest] |
| [IIAMS-103](https://logimarui.atlassian.net/browse/IIAMS-103) — Cobrir conflitos, startup isolado e testes contractuais | IT | [PermissionCatalogUseCases][PermissionCatalogUseCases] | [PermissionCatalogIntegrationTest][PermissionCatalogIntegrationTest], [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |

### IIAMS-84

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-104](https://logimarui.atlassian.net/browse/IIAMS-104) — Evoluir Role para ownership GLOBAL/SCOPE/TENANT | IT | [Role][Role] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-105](https://logimarui.atlassian.net/browse/IIAMS-105) — Implementar regras de disponibilidade de Role na hierarquia | IT | [Role][Role] | [EffectiveAuthorizationEvaluatorTest][EffectiveAuthorizationEvaluatorTest], [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-106](https://logimarui.atlassian.net/browse/IIAMS-106) — Criar casos de uso de administração de Roles por proprietário | IT | [RoleAdministrationUseCases][RoleAdministrationUseCases] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest] |
| [IIAMS-107](https://logimarui.atlassian.net/browse/IIAMS-107) — Propagar mudanças de Role compartilhada a assignments existentes | IT | [RoleAdministrationUseCases][RoleAdministrationUseCases] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |
| [IIAMS-108](https://logimarui.atlassian.net/browse/IIAMS-108) — Testar isolamento de proprietário, composição e lifecycle | IT | [Role][Role] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest] |

### IIAMS-85

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-109](https://logimarui.atlassian.net/browse/IIAMS-109) — Evoluir domínio RoleAssignment e migração de IdentityRole | IT | [RoleAssignment][RoleAssignment] | [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest] |
| [IIAMS-110](https://logimarui.atlassian.net/browse/IIAMS-110) — Criar gestão transacional de atribuições e revogações | IT | [RoleAdministrationUseCases][RoleAdministrationUseCases] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-111](https://logimarui.atlassian.net/browse/IIAMS-111) — Implementar resolução de Roles efetivas via ancestrais | IT | [EffectiveAuthorizationEvaluator][EffectiveAuthorizationEvaluator] | [EffectiveAuthorizationEvaluatorTest][EffectiveAuthorizationEvaluatorTest] |
| [IIAMS-112](https://logimarui.atlassian.net/browse/IIAMS-112) — Separar RoleAssignment do domínio Affiliation | IT | [RoleAssignment][RoleAssignment] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-113](https://logimarui.atlassian.net/browse/IIAMS-113) — Testar herança, reparenting, revogação e upgrade | IT | [ScopeAdministrationUseCases][ScopeAdministrationUseCases] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest] |

### IIAMS-86

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-114](https://logimarui.atlassian.net/browse/IIAMS-114) — Criar domínio e persistência de DENY Identity/Role | IT | [DenyRule][DenyRule] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest] |
| [IIAMS-115](https://logimarui.atlassian.net/browse/IIAMS-115) — Implementar administração segura de DENYs | IT | [DenyDelegationUseCases][DenyDelegationUseCases] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest] |
| [IIAMS-116](https://logimarui.atlassian.net/browse/IIAMS-116) — Construir avaliação DENY-first em hierarquia e Roles | IT | [EffectiveAuthorizationEvaluator][EffectiveAuthorizationEvaluator] | [EffectiveAuthorizationEvaluatorTest][EffectiveAuthorizationEvaluatorTest], [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-117](https://logimarui.atlassian.net/browse/IIAMS-117) — Integrar consistência pós-mudança e proteção de cache | IT | [EffectiveAuthorizationUseCases][EffectiveAuthorizationUseCases] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |
| [IIAMS-118](https://logimarui.atlassian.net/browse/IIAMS-118) — Testar matriz anti-bypass e precedência absoluta | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |

### IIAMS-87

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-119](https://logimarui.atlassian.net/browse/IIAMS-119) — Definir direitos de delegação explícitos por Permission+Scope | IT | [PermissionDelegation][PermissionDelegation] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-120](https://logimarui.atlassian.net/browse/IIAMS-120) — Aplicar guardrails em RolePermission e Roles compartilhadas | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-121](https://logimarui.atlassian.net/browse/IIAMS-121) — Proteger RoleAssignments, Role ownership e gestão de Scope | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-122](https://logimarui.atlassian.net/browse/IIAMS-122) — Resguardar privilégios internos sensíveis e autoridade central | IT | [RootRecoveryEnvelope][RootRecoveryEnvelope] | [AuthorizationRootRecoveryIntegrationTest][AuthorizationRootRecoveryIntegrationTest], [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-123](https://logimarui.atlassian.net/browse/IIAMS-123) — Testar escalada transitiva, concorrência e autorização negativa | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |

### IIAMS-88

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-124](https://logimarui.atlassian.net/browse/IIAMS-124) — Definir contrato PDP, PEP e DTOs de decisão | IT | [ServiceAuthorizationController][ServiceAuthorizationController] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationPepClientTest][AuthorizationPepClientTest] |
| [IIAMS-125](https://logimarui.atlassian.net/browse/IIAMS-125) — Implementar autenticação e autorização M2M de consumidores | IT | [TechnicalServiceAuthenticationAdapter][TechnicalServiceAuthenticationAdapter] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |
| [IIAMS-126](https://logimarui.atlassian.net/browse/IIAMS-126) — Implementar evaluator central com hierarquia, DENY, ALLOW e revogação | IT | [PdpUseCases][PdpUseCases] | [EffectiveAuthorizationEvaluatorTest][EffectiveAuthorizationEvaluatorTest], [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |
| [IIAMS-127](https://logimarui.atlassian.net/browse/IIAMS-127) — Definir segurança de resolução do recurso e anti-spoof no PEP | IT | [AuthorizationPepClient][AuthorizationPepClient] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationPepClientTest][AuthorizationPepClientTest] |
| [IIAMS-128](https://logimarui.atlassian.net/browse/IIAMS-128) — Instrumentar fail-closed, observabilidade e limites de throughput | IR — limites nas seções 4/8/12 | [ServiceApiSecurityConfiguration][ServiceApiSecurityConfiguration] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationPepClientTest][AuthorizationPepClientTest], [AuthorizationPerformanceIntegrationTest][AuthorizationPerformanceIntegrationTest] |
| [IIAMS-129](https://logimarui.atlassian.net/browse/IIAMS-129) — Testar integração real entre serviço consumidor, Actor e PDP | IT | [AuthorizationPepClient][AuthorizationPepClient] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationPepClientTest][AuthorizationPepClientTest] |

### IIAMS-89

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-130](https://logimarui.atlassian.net/browse/IIAMS-130) — Especificar envelope mínimo de recuperação e policy ROOT | IT | [RootRecoveryEnvelope][RootRecoveryEnvelope] | [AuthorizationRootRecoveryIntegrationTest][AuthorizationRootRecoveryIntegrationTest] |
| [IIAMS-131](https://logimarui.atlassian.net/browse/IIAMS-131) — Impedir lockout por DENY, RoleAssignment e ciclo de vida | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [AuthorizationRootRecoveryIntegrationTest][AuthorizationRootRecoveryIntegrationTest] |
| [IIAMS-132](https://logimarui.atlassian.net/browse/IIAMS-132) — Proteger bootstrap e administração da Role ROOT | IT | [InitialRootAuthorizationAdapter][InitialRootAuthorizationAdapter] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [RootEligibilityTest][RootEligibilityTest] |
| [IIAMS-133](https://logimarui.atlassian.net/browse/IIAMS-133) — Projetar operação break-glass e auditoria de emergências | IR — limites nas seções 4/8/12 | [RootRecoveryUseCases][RootRecoveryUseCases] | [AuthorizationRootRecoveryIntegrationTest][AuthorizationRootRecoveryIntegrationTest], [runbook](OPERATIONS.md) |
| [IIAMS-134](https://logimarui.atlassian.net/browse/IIAMS-134) — Testar negativas, DENY comum e perda do último admin | IT | [RootRecoveryUseCases][RootRecoveryUseCases] | [AuthorizationRootRecoveryIntegrationTest][AuthorizationRootRecoveryIntegrationTest] |

### IIAMS-90

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-135](https://logimarui.atlassian.net/browse/IIAMS-135) — Criar controllers e contratos OpenAPI para gestão de Scopes e Roles | IT | [AuthorizationAdminController][AuthorizationAdminController] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest] |
| [IIAMS-136](https://logimarui.atlassian.net/browse/IIAMS-136) — Publicar APIs de grants, RoleAssignments, delegação e DENY | IT | [AuthorizationAdminController][AuthorizationAdminController] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest], [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-137](https://logimarui.atlassian.net/browse/IIAMS-137) — Construir visão de Roles disponíveis por Tenant/Scope | IT | [AuthorizationQueries][AuthorizationQueries] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest] |
| [IIAMS-138](https://logimarui.atlassian.net/browse/IIAMS-138) — Implementar consultas de permissões efetivas e motivos de decisão | IT | [AuthorizationQueries][AuthorizationQueries] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest] |
| [IIAMS-139](https://logimarui.atlassian.net/browse/IIAMS-139) — Documentar integração administrativa e testar isolamento | IT | [AuthorizationQueries][AuthorizationQueries] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest], [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |

### IIAMS-91

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-140](https://logimarui.atlassian.net/browse/IIAMS-140) — Definir esquema de auditoria de segurança e decisão PDP | IT | [JdbcSecurityAuditEvents][JdbcSecurityAuditEvents] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest], [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |
| [IIAMS-141](https://logimarui.atlassian.net/browse/IIAMS-141) — Versionar alterações e invalidar caches sem grants obsoletos | IT | [JdbcAuthorizationPolicyRepository][JdbcAuthorizationPolicyRepository] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-142](https://logimarui.atlassian.net/browse/IIAMS-142) — Garantir propagação atômica de Role compartilhada e reparent | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest] |
| [IIAMS-143](https://logimarui.atlassian.net/browse/IIAMS-143) — Instrumentar métricas, logs de decisão e observabilidade | IR — limites nas seções 4/8/12 | [PdpUseCases][PdpUseCases] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationPerformanceIntegrationTest][AuthorizationPerformanceIntegrationTest] |
| [IIAMS-144](https://logimarui.atlassian.net/browse/IIAMS-144) — Testar consistência temporal, impacto e auditoria completa | IT | [JdbcSecurityAuditRepository][JdbcSecurityAuditRepository] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest], [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest] |

### IIAMS-92

| Subtarefa e entrega Jira | Estado | Implementação | Testes/evidências |
| --- | --- | --- | --- |
| [IIAMS-145](https://logimarui.atlassian.net/browse/IIAMS-145) — Inspecionar classes atuais, Flyway V9 e riscos de compatibilidade | IT | [JdbcAuthorizationMutationRepository][JdbcAuthorizationMutationRepository] | [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest], [auditoria](legacy-audit.md) |
| [IIAMS-146](https://logimarui.atlassian.net/browse/IIAMS-146) — Criar migrações PostgreSQL e verificações de upgrade realista | IT | [JdbcAuthorizationMutationRepository][JdbcAuthorizationMutationRepository] | [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest], [ScopePersistenceIntegrationTest][ScopePersistenceIntegrationTest], [migrations](OPERATIONS.md#antes-de-atualizar) |
| [IIAMS-147](https://logimarui.atlassian.net/browse/IIAMS-147) — Adicionar suíte de autorização hierárquica e anti-escalada | IT | [AuthorizationMutationGuard][AuthorizationMutationGuard] | [AuthorizationAdministrationIntegrationTest][AuthorizationAdministrationIntegrationTest], [EffectiveAuthorizationEvaluatorTest][EffectiveAuthorizationEvaluatorTest] |
| [IIAMS-148](https://logimarui.atlassian.net/browse/IIAMS-148) — Criar testes de integração API/M2M/PDP e segurança | IT | [ServiceAuthorizationController][ServiceAuthorizationController] | [AuthorizationServiceIntegrationTest][AuthorizationServiceIntegrationTest], [AuthorizationPepClientTest][AuthorizationPepClientTest] |
| [IIAMS-149](https://logimarui.atlassian.net/browse/IIAMS-149) — Revisar docs, contratos e rollout do Authorization | IT | [AuthorizationOpenApiConfiguration][AuthorizationOpenApiConfiguration] | [AuthorizationAdminHttpIntegrationTest][AuthorizationAdminHttpIntegrationTest], [guias](ARCHITECTURE.md) e [OpenAPI](openapi.json) |
| [IIAMS-150](https://logimarui.atlassian.net/browse/IIAMS-150) — Executar regressão final da fundação e consolidar CI | IT | [EffectiveAuthorizationUseCases][EffectiveAuthorizationUseCases] | [AuthorizationUpgradeIntegrationTest][AuthorizationUpgradeIntegrationTest], [42 suites / 320 testes](round-2-test-results.json), CI da seção 11 |

[AuthorizationScope]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/AuthorizationScope.java
[ScopeHierarchyTest]: ../../src/test/java/br/com/imarui/ima/authorization/core/domain/ScopeHierarchyTest.java
[JdbcScopeRepository]: ../../src/main/java/br/com/imarui/ima/authorization/infra/persistence/JdbcScopeRepository.java
[ScopePersistenceIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/ScopePersistenceIntegrationTest.java
[AuthorizationPerformanceIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationPerformanceIntegrationTest.java
[AuthorizationUpgradeIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationUpgradeIntegrationTest.java
[ScopeAdministrationUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/ScopeAdministrationUseCases.java
[AuthorizationAdministrationIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationAdministrationIntegrationTest.java
[ScopeHierarchy]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/policy/ScopeHierarchy.java
[Permission]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/Permission.java
[PermissionCatalogIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/PermissionCatalogIntegrationTest.java
[PermissionManifest]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/PermissionManifest.java
[PermissionManifestTest]: ../../src/test/java/br/com/imarui/ima/authorization/core/domain/PermissionManifestTest.java
[AuthenticatedCatalogServiceAdapter]: ../../src/main/java/br/com/imarui/ima/authorization/infra/security/AuthenticatedCatalogServiceAdapter.java
[AuthorizationServiceIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationServiceIntegrationTest.java
[PermissionCatalogBootstrap]: ../../src/main/java/br/com/imarui/ima/authorization/infra/bootstrap/PermissionCatalogBootstrap.java
[PermissionCatalogUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/PermissionCatalogUseCases.java
[Role]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/Role.java
[EffectiveAuthorizationEvaluatorTest]: ../../src/test/java/br/com/imarui/ima/authorization/core/domain/EffectiveAuthorizationEvaluatorTest.java
[RoleAdministrationUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/RoleAdministrationUseCases.java
[AuthorizationAdminHttpIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationAdminHttpIntegrationTest.java
[RoleAssignment]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/RoleAssignment.java
[EffectiveAuthorizationEvaluator]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/policy/EffectiveAuthorizationEvaluator.java
[DenyRule]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/DenyRule.java
[DenyDelegationUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/DenyDelegationUseCases.java
[EffectiveAuthorizationUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/EffectiveAuthorizationUseCases.java
[AuthorizationMutationGuard]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/service/AuthorizationMutationGuard.java
[PermissionDelegation]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/model/PermissionDelegation.java
[RootRecoveryEnvelope]: ../../src/main/java/br/com/imarui/ima/authorization/core/domain/policy/RootRecoveryEnvelope.java
[AuthorizationRootRecoveryIntegrationTest]: ../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationRootRecoveryIntegrationTest.java
[ServiceAuthorizationController]: ../../src/main/java/br/com/imarui/ima/authorization/api/http/ServiceAuthorizationController.java
[AuthorizationPepClientTest]: ../../src/test/java/br/com/imarui/ima/authorization/core/AuthorizationPepClientTest.java
[TechnicalServiceAuthenticationAdapter]: ../../src/main/java/br/com/imarui/ima/authentication/infra/security/TechnicalServiceAuthenticationAdapter.java
[PdpUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/PdpUseCases.java
[AuthorizationPepClient]: ../../src/main/java/br/com/imarui/ima/authorization/api/pep/AuthorizationPepClient.java
[ServiceApiSecurityConfiguration]: ../../src/main/java/br/com/imarui/ima/platform/security/ServiceApiSecurityConfiguration.java
[InitialRootAuthorizationAdapter]: ../../src/main/java/br/com/imarui/ima/authorization/infra/bootstrap/InitialRootAuthorizationAdapter.java
[RootEligibilityTest]: ../../src/test/java/br/com/imarui/ima/authorization/core/application/RootEligibilityTest.java
[RootRecoveryUseCases]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/RootRecoveryUseCases.java
[AuthorizationAdminController]: ../../src/main/java/br/com/imarui/ima/authorization/api/http/AuthorizationAdminController.java
[AuthorizationQueries]: ../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/AuthorizationQueries.java
[JdbcSecurityAuditEvents]: ../../src/main/java/br/com/imarui/ima/authorization/infra/persistence/JdbcSecurityAuditEvents.java
[JdbcAuthorizationPolicyRepository]: ../../src/main/java/br/com/imarui/ima/authorization/infra/persistence/JdbcAuthorizationPolicyRepository.java
[JdbcSecurityAuditRepository]: ../../src/main/java/br/com/imarui/ima/authorization/infra/persistence/JdbcSecurityAuditRepository.java
[JdbcAuthorizationMutationRepository]: ../../src/main/java/br/com/imarui/ima/authorization/infra/persistence/JdbcAuthorizationMutationRepository.java
[AuthorizationOpenApiConfiguration]: ../../src/main/java/br/com/imarui/ima/platform/openapi/AuthorizationOpenApiConfiguration.java

## 14. Integração, operação e entrega para revisão

Ler [arquitetura](ARCHITECTURE.md), [consumidor](CONSUMER_GUIDE.md), [operação](OPERATIONS.md)
e [OpenAPI](openapi.json). O PR #3 reúne ambas as rodadas e mantém histórico publicado.
O consumidor integra com Authentication técnico e PDP; precisa resolver seu recurso no backend,
validar resposta e negar em falha. Nenhuma integração exige alteração do Gateway nesta entrega.

Antes de futura produção, revisar PR, testar backup/preflight em ambiente próprio e aprovar
integração operacional. Cadastrar owner/chave pública, jurisdição e operações sob autorização,
publicar manifesto, aprovar primeira delegação e atribuir Roles explicitamente. Monitorar
revogações, correlação, expiração de chave, 429/503 e custo da política compartilhada.
Esta entrega não altera status formal de aceite, não faz merge e não executa deploy.
