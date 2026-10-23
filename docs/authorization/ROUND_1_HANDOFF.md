# Authorization IIAM — handoff da Rodada 1

> Registro histórico preservado. A Rodada 2 continua esta implementação e substitui os itens
> pendentes abaixo; consultar o [relatório final](ROUND_2_FINAL_REPORT.md) e a
> [arquitetura vigente](ARCHITECTURE.md). O Rovo Legacy voltou a responder na Rodada 2:
> as 11 Stories e 58 subtarefas foram recuperadas, inclusive os critérios antes indisponíveis.
> Os 262 testes e as limitações descritas aqui correspondem ao checkpoint da Rodada 1.

Data: 2026-10-22. Branch: `feat/iiams-81-authorization-foundation`.
Base de integração: `main`, inicialmente `f419a501328774fefec24b6ac28070e19f972cf8`.

## 1. Estado e escopo

Implementado o núcleo interno das seis Stories IIAMS-82–87 conforme o anexo da Rodada 1:
Scopes, catálogo, ownership de Roles, RoleAssignments, avaliação, DENY e delegação.
Há persistência real, casos de uso protegidos, integração com Identity/Authentication,
auditoria transacional e testes PostgreSQL. Não há nova API HTTP de administração ou PDP.

**Limite de aceite:** o Rovo Legacy expirou mesmo nas leituras individuais de IIAMS-81
e IIAMS-82. A implementação seguiu o anexo recuperado, como ele autoriza. As descrições
individuais de IIAMS-93–123 não foram recuperadas; a matriz abaixo não afirma que seus
critérios formais foram integralmente conferidos. Nenhuma issue ou Sprint foi alterada.

IIAMS-88–92, Gateway, integração final do Epic, merge e deploy permanecem fora desta rodada.
O código é publicado para revisão intermediária; o Epic não foi encerrado.

## 2. Evidências por Story

### S82 — Scopes

- Domínio: `AuthorizationScope`, `ScopeHierarchy`.
- Persistência: `ScopeRepository`, `JdbcScopeRepository`; V11 e V17.
- Administração: `ScopeAdministrationUseCases`, `AuthorizationMutationGuard`.
- Testes: `ScopeHierarchyTest`, `ScopePersistenceIntegrationTest`,
  `AuthorizationUpgradeIntegrationTest` e cenários de hierarquia, reparenting e
  concorrência em `AuthorizationAdministrationIntegrationTest`.
- Cobertura: UUID/código estáveis, múltiplas raízes, integridade/ciclos, ancestrais e
  descendentes, reparenting com análise de ganhos, binding Tenant imutável, rollback
  e proteção de atribuições diretas e recuperação.

### S83 — Catálogo

- Domínio: `Permission`, `PermissionManifest`; enums somente para definições internas.
- Aplicação: `PermissionCatalogUseCases`, `CatalogOwnerAdministrationUseCases` e
  aprovação inicial explícita em `DenyDelegationUseCases.bootstrapCatalogAuthority`.
- Persistência: `PermissionCatalogRepository`, `JdbcPermissionCatalogRepository`; V12/V17.
- Testes: `PermissionManifestTest`, `PermissionCatalogIntegrationTest` e cenários de
  governança externa em `AuthorizationAdministrationIntegrationTest`.
- Cobertura: owner/namespace, códigos únicos, digest e versões, idempotência, conflito
  concorrente, último manifesto válido, depreciação, preservação de referências e
  ausência de grants na sincronização. Sem adapter técnico, a chamada externa é negada.

### S84 — Roles

- Domínio: `Role.Ownership` GLOBAL/SCOPE/TENANT; `RolePermission` reaproveitado.
- Aplicação: `RoleAdministrationUseCases`; V13/V17.
- Testes: `EffectiveAuthorizationEvaluatorTest`, cenários de ownership, unicidade,
  edição compartilhada e concorrência em `AuthorizationAdministrationIntegrationTest`.
- Cobertura: disponibilidade distinta de atribuição, administração no proprietário,
  nomes por proprietário, IDs estáveis, composição compartilhada, ROOT protegido.

### S85 — Atribuições e herança

- Domínio: `RoleAssignment`, `AuthorizationPolicyState`, `EffectiveAuthorizationEvaluator`.
- Integração: `EffectiveAuthorizationUseCases`, `AuthorizationIdentityAuthoritiesProvider`,
  `IdentityAuthorizationApi`, `TenantAuthorizationAccess`; V13.
- Testes: algoritmo, migração V10 populada, grants sem Affiliation, HTTP por Tenant,
  revogação com o mesmo JWT e preservação de atribuições diretas em reparenting.
- Cobertura: herança descendente, isolamento de irmãos/raízes, lifecycle e consulta
  atualizada sem reatribuição ou novo login.

### S86 — DENY

- Domínio: `DenyRule`; aplicação: `DenyDelegationUseCases`; V14/V15/V16.
- Testes: DENY Identity/Role, ancestral, conflito entre Roles, ALLOW específico contra
  DENY ancestral, ROOT ordinário, default deny, revogação e rollback.
- Cobertura: DENY é sempre anterior ao ALLOW. Não há prioridade numérica ou bypass ROOT.

### S87 — Delegação e escalada

- Domínio: `PermissionDelegation`; aplicação: `AuthorizationMutationGuard`,
  `RoleAdministrationUseCases`, `ScopeAdministrationUseCases`, `DenyDelegationUseCases`.
- Testes: uso sem delegação, concessão autorizada, self-grant direto/indireto, edição
  compartilhada com impacto em vários Scopes, remoção de DENY por reparenting,
  redelegação não autorizada, revogação concorrente e governança inicial do catálogo.
- Cobertura: autoridade por Permission/Scope, transatividade explícita, validação e
  escrita serializadas e auditoria atômica.

Código: [Authorization](../../src/main/java/br/com/imarui/ima/authorization/).
Testes: [Authorization](../../src/test/java/br/com/imarui/ima/authorization/).

## 3. Matriz individual das 31 subtarefas

`T` significa implementação técnica da Story coberta pelo anexo e pelos testes acima.
`J?` significa descrição/aceite individual do Jira indisponível. A associação de cada
subtarefa ao componente específico só pode ser refinada quando seu texto for recuperado.
Não foram inventados títulos nem distribuídos critérios desconhecidos entre os IDs.

| Subtarefa | Story | Evidência técnica do grupo | Estado real | Finding |
|---|---|---|---|---|
| IIAMS-93 | IIAMS-82 | [S82](#s82--scopes) | T / J? | F-01 |
| IIAMS-94 | IIAMS-82 | [S82](#s82--scopes) | T / J? | F-01 |
| IIAMS-95 | IIAMS-82 | [S82](#s82--scopes) | T / J? | F-01 |
| IIAMS-96 | IIAMS-82 | [S82](#s82--scopes) | T / J? | F-01 |
| IIAMS-97 | IIAMS-82 | [S82](#s82--scopes) | T / J? | F-01 |
| IIAMS-98 | IIAMS-83 | [S83](#s83--catálogo) | T / J? | F-01 |
| IIAMS-99 | IIAMS-83 | [S83](#s83--catálogo) | T / J? | F-01 |
| IIAMS-100 | IIAMS-83 | [S83](#s83--catálogo) | T / J? | F-01 |
| IIAMS-101 | IIAMS-83 | [S83](#s83--catálogo) | T / J? | F-01 |
| IIAMS-102 | IIAMS-83 | [S83](#s83--catálogo) | T / J? | F-01 |
| IIAMS-103 | IIAMS-83 | [S83](#s83--catálogo) | T / J? | F-01 |
| IIAMS-104 | IIAMS-84 | [S84](#s84--roles) | T / J? | F-01 |
| IIAMS-105 | IIAMS-84 | [S84](#s84--roles) | T / J? | F-01 |
| IIAMS-106 | IIAMS-84 | [S84](#s84--roles) | T / J? | F-01 |
| IIAMS-107 | IIAMS-84 | [S84](#s84--roles) | T / J? | F-01 |
| IIAMS-108 | IIAMS-84 | [S84](#s84--roles) | T / J? | F-01 |
| IIAMS-109 | IIAMS-85 | [S85](#s85--atribuições-e-herança) | T / J? | F-01 |
| IIAMS-110 | IIAMS-85 | [S85](#s85--atribuições-e-herança) | T / J? | F-01 |
| IIAMS-111 | IIAMS-85 | [S85](#s85--atribuições-e-herança) | T / J? | F-01 |
| IIAMS-112 | IIAMS-85 | [S85](#s85--atribuições-e-herança) | T / J? | F-01 |
| IIAMS-113 | IIAMS-85 | [S85](#s85--atribuições-e-herança) | T / J? | F-01 |
| IIAMS-114 | IIAMS-86 | [S86](#s86--deny) | T / J? | F-01 |
| IIAMS-115 | IIAMS-86 | [S86](#s86--deny) | T / J? | F-01 |
| IIAMS-116 | IIAMS-86 | [S86](#s86--deny) | T / J? | F-01 |
| IIAMS-117 | IIAMS-86 | [S86](#s86--deny) | T / J? | F-01 |
| IIAMS-118 | IIAMS-86 | [S86](#s86--deny) | T / J? | F-01 |
| IIAMS-119 | IIAMS-87 | [S87](#s87--delegação-e-escalada) | T / J? | F-01 |
| IIAMS-120 | IIAMS-87 | [S87](#s87--delegação-e-escalada) | T / J? | F-01 |
| IIAMS-121 | IIAMS-87 | [S87](#s87--delegação-e-escalada) | T / J? | F-01 |
| IIAMS-122 | IIAMS-87 | [S87](#s87--delegação-e-escalada) | T / J? | F-01 |
| IIAMS-123 | IIAMS-87 | [S87](#s87--delegação-e-escalada) | T / J? | F-01 |

## 4. Reconciliação do legado

Auditoria inicial completa: [legacy-audit.md](legacy-audit.md).

| Tratamento | Resultado entregue |
|---|---|
| REUSE | Invariantes e IDs de RolePermission, exceções de descrição, padrões JDBC/ports/transações, Clock, JWT/sessões, arquivos protegidos e bootstrap único |
| REFACTOR | Permission deixa de depender de enum fechado; catálogo tem owner/namespace/versão; Role ganha ownership; enums internos continuam como definições próprias; adapters e bootstrap preservam IDs/contratos válidos |
| REPLACE | IdentityRole vira RoleAssignment; provider plano PLATFORM usa o avaliador comum; consulta do recurso Tenant usa seu binding persistido |
| REMOVE | Classe IdentityRole e algoritmo antigo baseado no campo textual PLATFORM; cross join que concedia qualquer Permission persistida ao ROOT; defaults de novas escritas que inventavam Scope/proprietário |

Não foram copiados novos componentes do Jeep Club. As migrations históricas permanecem.
Não houve recriação de Identity/Tenant, hierarquia paralela em Identity ou exigência de Affiliation.

## 5. Estrutura final

```text
authorization/api/catalog                  definições internas
authorization/api/module                   IdentityAuthorities, InitialRootAuthorization
authorization/core/domain/model            Scope, Permission/manifesto, Role, RolePermission,
                                           RoleAssignment, DenyRule, PermissionDelegation
authorization/core/domain/policy           árvore, revisão de política, avaliador puro
authorization/core/application/command      motivo e correlação
authorization/core/application/port         ator e prova técnica de catálogo
authorization/core/application/service      guard de mutações, impacto e recuperação
authorization/core/application/usecase      Scope, Role, DENY/delegação, catálogo e avaliação
authorization/core/repository              contratos de leitura/escrita
authorization/infra/persistence             adapters JDBC
authorization/infra/security                ator, authorities e enforcement Tenant
authorization/infra/bootstrap               catálogo interno e atribuição ROOT inicial
```

Identity publica `IdentityAuthorizationApi` com projeções mínimas de lifecycle.
Platform continua responsável por HTTP/JWT e pelo início do bootstrap administrativo.

## 6. Entidades e relacionamentos

- Scope UUID → parent Scope opcional; código canônico global `[A-Z][A-Z0-9_]{0,79}`.
- Tenant UUID → binding único e estável → Scope UUID distinto. Scope não possui Tenant obrigatório.
- Permission bigint → código global estável, owner/namespace, versão declarada e depreciação.
- Owner de catálogo → revisões imutáveis por aplicação, com versão/digest/manifesto persistidos.
- Role bigint → GLOBAL, SCOPE ou TENANT; owner imutável e nome único por proprietário.
- RolePermission mantém o par Role/Permission e os IDs existentes quando não é removida.
- RoleAssignment UUID → Identity UUID + Role bigint + Scope UUID; ativo/revogado e versão.
- DENY UUID → Permission + Scope + exatamente uma Identity ou Role.
- Delegação UUID → Identity + Permission + Scope, flag de redelegação, lifecycle e motivo.
- Auditoria independente → ator/serviço, alvo, Scope, motivo, instante UTC, correlação e antes/depois.

Nomes de Role preservam a semântica anterior sensível a maiúsculas/minúsculas. ROOT é reservado.
Roles TENANT alcançam o Scope do próprio Tenant; não concedem direitos a outros Tenants abaixo dele.

## 7. Decisões de transição e governança

1. **PLATFORM:** V13 converte cada vínculo legado para a raiz administrativa explícita
   `00000000-0000-0000-0000-000000000001` (`IIAM_PLATFORM`). Tenants existentes são seus
   descendentes iniciais. Essa raiz não representa geografia e não é wildcard de outras raízes.
2. **Binding:** V11 cria um Scope por Tenant existente e um trigger transacional para novos
   Tenants. O binding não pode ser trocado/removido; reparenting preserva o UUID e os grants diretos.
3. **Movimentos inválidos:** se mover um Scope tornaria uma Role direta indisponível pelo
   ownership, a transação é rejeitada. Destacar uma subárvore sem governança ROOT própria
   também é rejeitado; crie uma raiz administrada e mova o recurso para ela.
4. **Raiz independente:** exige ROOT existente, capacidade central e uso/redelegação de
   cada direito de origem. Cria atribuição e delegações explícitas para o operador; não copia
   a Role nem estende grants de terceiros. Uma restrição de origem impede esse caminho.
5. **Catálogo:** sincronização e grants são operações distintas. O primeiro delegador de uma
   Permission externa é aprovado explicitamente por ROOT com CATALOG_MANAGE na fronteira
   central e DELEGATION_MANAGE na raiz de destino. Exige código externo vigente, raiz
   administrada, ausência de delegação histórica nessa jurisdição e ausência de DENY aplicável.
   Cria somente autoridade de delegação; não concede uso. Não reabre autoridade revogada.
6. **ROOT:** definição, atribuições essenciais e autoridade de bootstrap são protegidas.
   As seis AUTHORIZATION_* formam o mínimo de recuperação em sua jurisdição. A proteção
   rejeita a mutação inválida; o avaliador continua aplicando DENY a ROOT normalmente.
7. **Lifecycle:** Tenant inativo bloqueia seu uso operacional; não desativa outro Tenant
   descendente. Scope inativo bloqueia a subárvore. TENANT_READ/MANAGE e operações próprias
   de governança permitem administrar lifecycle sem ativar Permissions de negócio.
8. **Depreciação:** grants existentes continuam referenciáveis/utilizáveis. Novas concessões
   dependem de Permission vigente; retirar código de manifesto não apaga linha ou histórico.
9. **Consistência:** avaliação usa transação própria REPEATABLE_READ; mutações usam lock
   transacional `810082`, estado atual, impacto e auditoria no mesmo commit. Não há cache JWT.

As decisões 3–6 exigem revisão de negócio junto às issues assim que o Legacy estiver acessível;
não foram apresentadas como transcrição de critérios individuais indisponíveis.

## 8. Migrations e upgrade

| Versão | Evolução |
|---|---|
| V11 | árvore, binding de Tenant, auditoria, integridade/ciclos e criação transacional de Scope |
| V12 | owners, revisões e metadados federados de Permission |
| V13 | ownership de Role, unicidade por owner e migração integral para RoleAssignment UUID |
| V14 | DENYs e delegações explícitas |
| V15 | autoridade inicial de ROOT existente, auditoria da migração, última Identity/assignment e definição ROOT protegidas |
| V16 | snapshot de linhas de autorização removidas, correlacionado ao IdentityHistory |
| V17 | exige Scope/owner/versão explícitos em novas escritas; namespace externo validado no banco |

V1–V10 não são reescritas. O teste V10 populado preserva IDs de Role/Permission/RolePermission,
Identity/Role/grant e timestamps, mantém Identity/Tenant e Session/Refresh ativos e valida Flyway.
A instalação limpa também é migrada e validada. Não executar baseline automático ou reset
de base compartilhada. Nenhum banco de produção foi usado nesta rodada.

## 9. Contratos internos

| Contrato | Uso e proteção |
|---|---|
| `ScopeAdministrationUseCases.create/revise` | gestão de árvore com ator, administração, impacto, proteção de grants e auditoria |
| `RoleAdministrationUseCases.create/revise/assign/revokeAssignment` | owner e capacidades distintos; composição/delegação e impacto verificados |
| `DenyDelegationUseCases` | criar/revogar DENY, delegar/revogar autoridade e aprovação central inicial do catálogo |
| `CatalogOwnerAdministrationUseCases.enroll` | inscreve somente owner/namespace de Permissions; não configura serviço operacional |
| `PermissionCatalogUseCases.synchronizeInternal` | manifesto interno fixo; versão inicial 1; não recebe dados externos |
| `PermissionCatalogUseCases.synchronizeExternal` | requer `CatalogServiceAuthentication`; rejeita ausência de adapter, owner falso e namespace alheio |
| `EffectiveAuthorizationUseCases.evaluate` | entrada interna Identity/Permission/Scope já resolvido; retorna allowed/reason de revisão consistente |
| `InitialRootAuthorization.assign` | somente no bootstrap único e transacional, com marcador/prova existente |

Mutações exigem `PolicyChangeReason` não vazio e correlation UUID. `AuthorizationActor`
adapta o ator confiável atual; payload não escolhe o ator. Repositórios de mutação exigem
transação existente e não constituem uma API de cliente.

## 10. Fluxo de avaliação e DENY

```text
Identity ativa + Permission conhecida + recurso disponível
  -> Roles de assignments ativos em ancestrais, respeitando disponibilidade/ownership
  -> DENY Identity ou Role efetiva em ancestral ou no próprio Scope? DENIED
  -> alguma Role efetiva contém a Permission? ALLOWED
  -> caso contrário: DENIED
```

Ancestrais/descendentes incluem o próprio Scope. Não há propagação para ancestrais,
irmãos ou raízes independentes, especificidade de ALLOW ou prioridade que vença DENY.
Estado indisponível ou árvore inválida não concede acesso. As Roles não são clonadas.

## 11. Modelo de delegação e alterações indiretas

Uso, gestão de Role, atribuição, delegação e gestão de Scope são capacidades distintas.
Criar/alterar composição exige o direito explícito de cada Permission na jurisdição.
Atribuir exige administração e delegação de toda a composição na abrangência de destino.
Autorizar a delegação a outro destinatário exige uma autoridade redelegável; a flag do
novo destinatário não elimina esse requisito.

Self-assignment e adição de Permissions a Role atribuída ao próprio ator são proibidos.
O guard compara o efeito antes/depois em destinatários e Scopes afetados, incluindo
potencial reativação. Ganhos por remoção de Role/DENY ou mudança de parent também
exigem autoridade anterior; um ganho para o próprio ator é rejeitado.

Autoridade de ancestral ativo pode configurar um descendente inativo; não ativa seu uso.
Autoridade cujo próprio Scope/ancestral está inativo não pode ser exercida. DENYs aplicáveis
também impedem delegação. Capacidade administrativa crítica do IIAM exige governo central.

## 12. Integração com Identity

Identity/Tenant/Affiliation/IdentityApplication continuam proprietários de seus dados.
Projeções mínimas de lifecycle são lidas na mesma revisão da política, sem consultar
Affiliation para conferir autorização geral. O vínculo continua exigível por negócios próprios.

As APIs Tenant obtêm Scope do binding do UUID do recurso. Um `scopeId` de frontend
não altera essa propriedade. Consulta/lifecycle permanecem nos contratos existentes.
Diretório global Identity e administração global Authentication continuam na fronteira
PLATFORM explícita; grants locais não viram authorities globais por união.

Exclusão física de Identity mantém os históricos existentes. V16 grava estado anterior
dos assignments/DENYs/delegações removidos por cascata, sem FK operacional no audit e
com a mesma correlação IdentityHistory. Falhas revertem arquivamento, revogação e exclusão.

## 13. Integração com Authentication

Login, RS256/JWKS, audience/issuer, access token, sessões, refresh/rotação, logout,
revogação, challenges e bootstrap protegido continuam em Authentication/Platform.
O converter recarrega authorities PLATFORM pela revisão vigente, sem confiar nas claims
de Permission do JWT. A guarda Tenant avalia o Scope real do recurso em cada consulta.

Testes demonstram revogação de Permission com o mesmo JWT e proteção de último ROOT
sem revogar sua sessão/outbox em uma operação recusada. Os helpers de teste usam o
Clock da aplicação, na precisão de microssegundos usada em produção.

## 14. Testes e reprodução

Evidência estruturada: [round-1-test-results.json](round-1-test-results.json).
Baseline histórico: [baseline-validation.md](baseline-validation.md).

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\Users\lucas.l\.jdks\ms-17.0.18'
```

O runner usa PostgreSQL 17 descartável em Docker. Nesta máquina o engine Docker não
estava disponível; as execuções locais usaram PostgreSQL nativo 18.4 em cluster/porta
temporários de loopback, com `IDENTITY_TEST_DB_URL` apontando somente para esse banco.
Executar `mvnw.cmd -B clean verify` com Java 17 nessa configuração aplica Flyway e valida JPA.

Validação local final: **262 testes em 37 suítes; zero falhas, erros ou ignorados;
BUILD SUCCESS e JAR executável**. São **71 testes adicionais aos 191 existentes**.
V1–V10 foram comparadas com a base Git e permanecem sem alterações. `git diff --check`
passou; a verificação de padrões de chaves/credenciais no diff não encontrou ocorrências.
Não há formatter/lint adicional configurado no projeto; não se declara execução inexistente.
O workflow também contempla a branch desta rodada e usa PostgreSQL 17/Linux no GitHub.

Os testes existentes não foram substituídos. Casos novos cobrem domínio, PostgreSQL,
HTTP, concorrência, rollback real, manifests conflitantes e upgrades preservando dados.

| Suíte adicionada | Casos |
|---|---:|
| ScopeHierarchyTest | 5 |
| ScopePersistenceIntegrationTest | 4 |
| PermissionManifestTest | 3 |
| PermissionCatalogIntegrationTest | 7 |
| EffectiveAuthorizationEvaluatorTest | 16 |
| AuthorizationUpgradeIntegrationTest | 2 |
| AuthorizationAdministrationIntegrationTest | 33 |
| RootEligibilityTest | 1 |

A leitura por chamador READ_COMMITTED foi testada enquanto outra transação alternava
Role ativa sem Permission e Role inativa com Permission: nenhuma decisão concedeu
o privilégio inexistente em ambas as revisões válidas. Testes de revogação preservam
o cutoff real; o helper de Session/JWT foi alinhado ao Clock de produção, sem sleeps
ou tolerância nova na validação de segurança.

## 15. Findings e limites

| ID | Severidade/estado | Componente e impacto | Reprodução e tratamento |
|---|---|---|---|
| F-01 | Aceite formal pendente | IIAMS-82–87 / 31 subtarefas: texto individual não obtido | getJiraIssue Legacy para uma issue retorna MCP request timed out. Três tentativas isoladas, sem loop. Recuperar descrições e refinar a matriz; não transicionar issues agora |
| F-02 | Baixa, estratégia explícita | `JdbcAuthorizationPolicyRepository` lê revisão completa e mutações usam um lock global | Observar consultas completas no adapter. Prioridade foi consistência; não há benchmark de carga. Medir/filterar/otimizar na Rodada 2 sem introduzir cache obsoleto |

Problemas resolvidos durante a rodada: código fechado por enum; sincronização que concedia
qualquer Permission ao ROOT; Scope textual único; authority plana em recurso Tenant;
defaults que inventavam governança; remoção de atribuições diretas por reparenting;
escalada por mudanças indiretas; recuperação ROOT perdida; leitura em transação de
chamador com isolamento menor; rótulo legado com espaços interpretado como ROOT;
fixtures temporais inconsistentes com produção. Nomes legados são reconstituídos
exatamente, sem promover rótulos a ROOT; novas escritas rejeitam variantes reservadas.

Ausência de transporte M2M/PDP e de CRUD HTTP completo é o limite aprovado desta rodada,
não um endpoint inseguro ou substituição do núcleo por documentação.

## 16. Continuidade na Rodada 2

1. Partir desta branch/PR intermediário, conferir HEAD e `origin/main`; preservar os commits.
2. Recuperar as issues pelo **Rovo Legacy do LogImarui** e conferir a matriz/decisões 3–6.
3. IIAMS-88: autenticação técnica real por serviço e PDP externo. Reusar o algoritmo;
   o adapter deve provar o owner, não aceitar nome de serviço do body/header como identidade.
4. IIAMS-89: recuperação administrativa integral e transferência controlada de ROOT.
   Não remover as proteções atuais antes de demonstrar o substituto e seus testes.
5. IIAMS-90: DTOs/controllers/erros/consultas explicativas protegidos, com Scope resolvido
   do recurso, sem transformar disponibilidade de Role ou sessão ativa em concessão.
6. IIAMS-91: auditoria/observabilidade/correlação e propagação avançadas. Preservar efeito
   após commit e atualização de JWT ainda válido; otimização não pode ressuscitar grants.
7. IIAMS-92: hardening/regressão ponta a ponta e migrations adicionais. Não alterar V1–V17.
8. Se mudar definições internas, incrementar a versão de `PermissionManifest.internal()`;
   o conflito de digest na mesma versão é intencional. Não contornar a governança externa
   registrando serviços, RolePermissions ou delegações por SQL de aplicação.

Gateway IIAMS-80 continua independente. Não implementar habilitação por Tenant ou
TenantServiceAccess no IIAM. Merge/deploy exigem a autorização apropriada posterior.

## 17. Pontos de retomada e riscos

O transporte externo do catálogo permanece indisponível até existir um adapter real
`CatalogServiceAuthentication`; nenhum endpoint aberto precisa ser mantido por compatibilidade.
O avaliador público interno lê política já confirmada em transação própria; validações de
mutações leem suas alterações pendentes na transação que mantém o lock.

O mínimo ROOT é protegido por prevenção de estado inválido. Isso não é um procedimento
completo de recuperação emergencial: a Rodada 2 deve desenhar, autorizar e testar esse fluxo.
Usar ambiente isolado e ensaios, preservar backups/checksums e nunca reparar produção
como efeito colateral dos testes. Não declarar as 31 subtarefas formalmente aprovadas
somente com base na cobertura agregada desta rodada.
