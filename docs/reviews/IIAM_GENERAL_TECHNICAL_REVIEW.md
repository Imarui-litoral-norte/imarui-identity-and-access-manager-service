# IIAM — auditoria técnica geral e integração entre módulos

Data: 23/10/2026. Escopo: aplicativo IIAM e bancos PostgreSQL descartáveis.
Branch: `audit/iiam-cross-module-review`, criada da default atualizada.
Sem merge, deploy, acesso a bases reais ou alteração do Gateway.

## 1. Baseline e pré-condições

- Default real, confirmada no GitHub e no remote HEAD: `main`.
- Baseline: `4a299044b767d11a0c7be0db33298043b570b9dd`.
- [PR #4](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/pull/4)
  integrado em 23/10/2026, 20:14:13 UTC. HEAD revisado `03464cb` é ancestral do baseline.
- [CI da main](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/actions/runs/40017647525):
  `completed/success`, conferido novamente nesta auditoria.
- Checkout inicialmente limpo; `main` sincronizada por fast-forward. Nova branch sem desenvolver
  sobre a branch antiga de Authorization.
- Baseline local: `mvnw.cmd -B clean verify`, **354 testes / 46 suites**, zero falhas, erros ou skips.
  Java 17.0.18, PostgreSQL 17.11 em container exclusivo; Hibernate `validate`; Flyway até V25.
- Rodadas 1–4 conferidas por ancestralidade, documentação, implementação e regressão executada.
  A presença dos documentos não foi usada como substituto da execução.

## 2. Arquitetura e inventário

| Módulo | Responsabilidade e caminhos examinados |
| --- | --- |
| Identity | Identity PERSON/LEGAL_ENTITY/SERVICE, CPF/CNPJ/nickname/contato, Tenant, Affiliation, IdentityApplication; domínio, casos de uso, controllers, mapeadores JPA, histórico e outbox de revogação |
| Authentication | Credential/BCrypt/lockout, challenges e entrega assíncrona, login, sessão stateful, refresh, logout, administração, revogação, bootstrap, RSA/JWKS e autenticação técnica |
| Authorization | Árvore de Scopes, binding de Tenant, catálogo, ownership/lifecycle de Roles, assignments, DENY/delegação, evaluator, guards, PDP/PEP, ROOT, journal, administração e credenciais M2M |
| Platform | Composição Spring, duas SecurityFilterChains, validação JWT stateful, integração de contexto humano, configuração, tratamento de erros, OpenAPI, CI e scripts de operação |

Regras permanecem em seus módulos. Os novos ports `IdentityOperationAccess` e
`AuthenticationOperationAccess` expressam exigências dos casos de uso; o adapter em Authorization
usa o evaluator central e o verificador humano existentes. Identity e Authentication não importam
o domínio de Authorization para decidir política. Não há novo evaluator, regra em controller,
cache permissivo ou dependência circular de beans. A inicialização real da aplicação é exercitada.

## 3. Findings comprovados

| ID | Severidade | Componente | Reprodução e comportamento anterior | Correção e regressão |
| --- | --- | --- | --- | --- |
| G-01 | HIGH | Identity ↔ Authentication ↔ Authorization | HTTP administrativo passa pelo filtro e aguarda lock da Identity. Revogar SID ou retirar assignment do operador durante a espera: disable ainda retorna **200**, muda o alvo e grava revogação; esperado **401/403**, alvo intacto | Verificar sessão e política no caso de uso após os locks de recurso. Aplicado também a Tenant, review, nickname, leitura, self-delete, convite e administração de sessão/refresh. `QueuedIdentityAdministrationTest`, `QueuedAuthenticationAdministrationTest`, `ModuleUseCaseAuthorizationTest` |
| G-02 | HIGH | Challenge ↔ lifecycle de Identity | Emitir PASSWORD_RESET, desativar, reativar sem executar worker e consumir o link anterior: senha alterada com sucesso; esperado rejeição pelo cutoff durável | Consumo de reset consulta `IdentityAccess.valid` com o instante original do challenge sob lock do titular. Link anterior falha; link novo e credencial antiga permanecem utilizáveis nos momentos adequados. `resetFromBeforeDisableCannotChangeCredentialsAfterReactivationWithoutWorker` |
| G-03 | MEDIUM | Authentication/login ↔ cadastro | Pausar após resolver nickname, alterar nickname ou desativar em outra transação e retomar: `ObjectOptimisticLockingFailureException` pelo objeto JPA descoberto antes do lock, sem resposta de autenticação controlada | Discovery usa projeção JDBC e relê o identificador após o lock. Corrida retorna `AuthenticationFailure` e não cria sessão; não foi demonstrado bypass por esse erro. `loginRechecksEligibilityAndIdentifierAfterDiscovery`, dois casos |
| G-04 | MEDIUM | Authentication/troca de senha | Cinco tentativas de senha atual incorreta: `failures=0`, sem aplicar a mesma política de lockout da Credential | Persistir `Credential.failed` e evento genérico `PASSWORD_CHANGE_FAILED` em falha autenticadora. Cinco falhas bloqueiam novas tentativas, inclusive com senha correta; exceções de infraestrutura continuam revertendo a transação. `failedCurrentPasswordAttemptsPersistAndUseCredentialLockout` |
| G-05 | MEDIUM | Identity/Tenant | PATCH name retorna nome novo; GET posterior retorna nome antigo. Use case era read-only e não salvava seu objeto de domínio destacado | Transação de escrita, lock do Tenant, autorização atual e `save`. Relógio lido após espera. `TenantHttpContractTest.tenantCreationRenameAndLifecycleMatchPublishedContract` agora confere GET posterior |

Primeira reprodução de G-02–G-05: seis testes, três assertions falhando e duas exceções de
concorrência. G-01 reproduzido separadamente nos dois cenários HTTP: **200 em vez de 401/403**.
Na extensão à troca de senha, o mesmo cenário de SID revogado após o filtro retornava **204 em vez
de 401**; a regressão adicional também precedeu sua correção.
As regressões mantêm as expectativas corretas; nenhuma foi removida para obter build verde.

## 4. Correções e fronteiras de segurança

- A autoridade convertida no filtro continua sendo uma primeira barreira. O caso de uso confere
  a política viva e a sessão depois da espera pelo recurso, inclusive ao ser chamado diretamente.
- Leitura e alteração de Identity global exigem a fronteira PLATFORM, com as exceções próprias
  de leitura/nickname/self-delete já publicadas. Affiliation não concede administração global.
- Tenant usa o binding oficial `authorization_tenant_scopes`, nunca um Scope livre no payload.
  Criação permanece PLATFORM. Administração de Tenant indisponível mantém sua semântica própria.
- Leitura de IdentityApplication usa `IDENTITY_APPLICATION_READ`; leitura/listagem para review
  usa `IDENTITY_APPLICATION_REVIEW`. A rota administrativa chama a operação explícita de review.
- SID consultado sem lock usa uma projeção JDBC atual, impedindo que revalidação reutilize uma
  Session antiga do persistence context. REPEATABLE READ do PDP conserva sua política de snapshot.
- Troca de senha, listagem e encerramento de sessões próprias conferem o titular atual. Uma
  prova de senha conhecida não autoriza outro ator a chamar a operação em nome do proprietário.
- Revalidação do Authorization após o lock administrativo (R3-08) foi preservada.
- Erros de sessão no ponto de uso retornam ProblemDetail 401; falta de Permission/DENY retorna
  403. Uma mutação rejeitada não modifica o alvo, não revoga suas sessões nem emite convite.

Fixtures antigas passaram a persistir concessões administrativas explícitas. Corridas propagam
o operador à thread. Reativação de um operador desativado usa outro administrador ativo.
Fixtures ROOT retiram as concessões auxiliares antes do bootstrap ROOT; nenhum privilégio
residual foi acrescentado às provas de transferência/lockout.

## 5. Identity

Revisados normalização/validação de CPF/CNPJ, nickname case-insensitive por kind, e-mail de contato,
conflitos de solicitações pendentes, locks de registro/aprovação, lifecycle, tipos e nulabilidade.
E-mail não vira identificador de login nem recebe unicidade global inventada.

Self-delete mantém histórico independente da Identity e de cada Affiliation, remoção física das
tabelas JOINED e pedido de revogação na mesma transação. Falhas de histórico/delete/outbox têm
rollback exercitado. Histórico não reserva chaves naturais; credenciais/challenges são removidos
pelas FKs e sessões históricas não se tornam acesso válido para uma Identity ausente.

G-05 corrige persistência, e G-01 acrescenta enforcement aos casos de uso existentes.
Não foram criadas APIs novas de CRUD global/affiliation, nem redefinido o escopo futuro da IIAMS-38.
Listagem atual de solicitações é deliberadamente sem paginação no contrato publicado; sua evolução
permanece no backlog, não foi disfarçada como implementação concluída.

## 6. Authentication

Revisados login por kind, dummy hash, BCrypt/limite de senha, lockout, primeiro acesso, reset e troca
de senha; sessões de sete dias e access de 900 s; hash/rotação/replay de refresh; logout próprio,
cookie/Bearer divergentes, expiração e administração de sessões/tokens.

Regressões existentes exercitam dois refreshes simultâneos (um vencedor e sessão comprometida
revogada), consumo concorrente de challenge, falha parcial de ativação, outbox atrasada/rollback,
logout durante uso, senha alterada e indisponibilidade simulada. G-02 cobre a lacuna específica
do challenge antigo sem esperar pelo worker; G-03/G-04 cobrem login e prova da senha atual.

JWT exige RS256/at+jwt, issuer/audience, UUID sub/sid, iat/exp/jti, TTL e sessão stateful elegível.
JWKS só publica material público; configuração fora de test/dev exige chave persistente.
Rotação de verificação tem regressão. Não se afirma rotação/homologação real de chaves em produção.
JWT offline externo pode sobreviver até exp; isso é o contrato documentado de até quinze minutos,
não uma promessa de revogação distribuída instantânea.

SMTP usa timeout, estados duráveis, no máximo cinco tentativas e lock entre workers; testes de
falha/restart/segredos preservados. Entrega real de e-mail e cookie/HTTPS do domínio não foram
homologados nesta auditoria local.

## 7. Authorization e integração

Revisão dirigida do evaluator, ScopeHierarchy, política potencial, guards, PDP e contexto técnico.
Ownership GLOBAL/SCOPE/TENANT, herança descendente, isolamento de irmãos, RoleAssignment
independente de Affiliation, DENY absoluto, delegação explícita e proteção de self-grant permanecem.
ROOT segue o evaluator comum, sem bypass universal; envelope de recuperação e último ROOT protegidos.

Preservadas regressões R3-01 (DENY na transferência), R3-06 (ganho indireto de delegação),
R3-08 (sessão na fila), F-03 (DENY duplicado), F-04 (Role lifecycle), F-05 (M2M) e F-02 (paths).
G-01 fecha a mesma classe de espera/revogação nos outros módulos, sem reconstruir Authorization.

M2M vincula chave/cliente/owner/jurisdição e prova RS256 ao método, path, digest do corpo e correlação.
Nonce persistido garante replay único; credencial/cliente são revalidados no snapshot/lock.
PDP confere Actor assinado, Permission do serviço e binding autoritativo do recurso. Suíte cobre
Actor trocado, Scope incorreto, Tenant irmão, tokens humanos/técnicos intercambiados, nonce
concorrente e falhas de dependência/journal. Falha na auditoria impede ALLOW confirmado.

O IIAM não comprova o banco privado de um consumidor desonesto: o PEP deve resolver a propriedade
real do recurso. Não se introduziu atomicidade distribuída nem se alterou o Gateway.

| Fluxo integrado | Evidência/resultado esperado |
| --- | --- |
| Primeiro acesso | Aprovação deixa PENDING; challenge/credential/ativação atômicos; login posterior; falha de ativação reverte credential e consumo |
| Desativação | Identity inativa e cutoff durável negam JWT/refresh; reativação não ressuscita sessão ou reset anterior |
| Alteração de política | Mesmo JWT reflete assignment/DENY atuais na requisição seguinte; operação na fila também revalida G-01 |
| Exclusão | Históricos independentes, relações tratadas e acesso residual negado; rollback mantém tudo consistente |
| Tenant | Binding estável, concessão contextual, Tenant indisponível nega negócio e conserva administração autorizada; irmãos isolados |
| Recuperação ROOT | Duas identidades/sessões/reautenticações, consumo único, DENY preservado, revogação da autoridade e journal atômicos |

## 8. HTTP e cenários negativos

Conferidos endpoints públicos (aplicação/solicitação de acesso/login/refresh/logout/complete,
CSRF/JWKS/health/OpenAPI), autenticados, administrativos e a chain técnica de maior prioridade.
Cookie Secure/HttpOnly/Path/SameSite, CORS de origens explícitas, CSRF para login/refresh/logout,
validação DTO, paginação administrativa de Authentication e ProblemDetail continuam.

Novas provas diretas dos use cases cobrem anônimo, Identity ativa sem Permission, DENY explícito
e concessão restrita ao Scope irmão, sem depender das annotations do controller. Provas HTTP
com locks reais cobrem SID revogado e assignment removido durante a espera em Identity,
convite, logout administrativo e revogação de refresh, além de troca de senha com SID revogado.
Asserções conferem também ausência de efeitos e rejeição de outro titular em chamadas diretas.
Testes administrativos e de PDP existentes complementam os grupos Tenant/Scope/Role/ROOT/M2M.

Não se declara ensaio de todos os endpoints em cada combinação possível de principal/recurso;
as provas cobrem cada grupo relevante e os pontos reais de enforcement compartilhados.

## 9. Persistência, migrations e concorrência

V1–V25 preservadas byte a byte contra o baseline; **nenhuma migration nova**.
Correções usam contratos/tabelas/índices existentes. Não houve edição de migration aplicada,
limpeza de base real ou reparação especulativa de dados históricos.

Validação inclui instalação limpa no baseline, Hibernate `validate`, upgrade populado V5/V9/V10/V17/V20,
V24→V25, integridade/constraints/triggers, serialização de refresh/challenges, ROOT e rollback.
Ensaio operacional usa dump custom V20, upgrade até V25, falha SQL, restore em outra base
descartável, novo upgrade, fingerprints e preflight somente leitura.

## 10. Operação e qualidade

Foram conferidos CI, preflight, runbooks, métricas protegidas, sinais afterCommit, logs estruturados,
capacidade de auditoria e benchmark F-02. Testes reexecutam os cenários sintéticos; resultados
atuais ficam nos artefatos desta revisão. Paths limitados continuam usando travessia segura no
fallback e o mesmo evaluator. Não há nova política de retenção, exclusão de journal ou TTL destrutivo.

Snapshot completo da política, checagem de impacto compartilhado e lock global continuam custos
de escala; páginas profundas/count e crescimento de auditoria precisam de acompanhamento.
Medições sintéticas não definem SLA, tamanho de produção ou RPO/RTO.
Refatorações foram limitadas aos pontos necessários para corrigir findings comprovados.

Evidência reexecutada nesta auditoria: [benchmark](general-review-operational-benchmark.json),
[capacidade/planos](general-review-audit-capacity.json) e [dump/restore](general-review-recovery-rehearsal.json).

## 11. Testes e evidências executáveis

- [Baseline por suite](general-review-baseline.json).
- [Resultado final por suite](general-review-test-results.json).
- Código novo de regressão: `CrossModuleCredentialIntegrationTest`,
  `QueuedIdentityAdministrationTest`, `QueuedAuthenticationAdministrationTest`,
  `ModuleUseCaseAuthorizationTest` e GET persistido no contrato de Tenant.
- Primeira rodada dirigida: 15 testes aprovados. Rodada completa intermediária encontrou fixtures
  sem operador/escopo e uma distinção READ/REVIEW; foram corrigidas mantendo suas assertions.
- **Validação final: 372 testes / 50 suites, zero falhas, erros e skips**, `mvnw.cmd -B clean verify`,
  Java 17.0.18/PostgreSQL 17.11, JAR gerado. Código validado: `0d4b69260a6a7682704f3c3451e83f6675073f26`.
  São 18 novos cenários, além de assertions adicionais no contrato de Tenant.
- Resultado remoto e HEAD exato de CI são registrados nos checks e na descrição do PR desta branch;
  são evidência separada da execução local. A consolidação documental não altera o código validado.

| Reprodução/garantia | Fonte executável |
| --- | --- |
| Login, reset e lockout | [CrossModuleCredentialIntegrationTest](../../src/test/java/br/com/imarui/ima/authentication/feature/CrossModuleCredentialIntegrationTest.java) |
| Sessão/política após espera de Identity | [QueuedIdentityAdministrationTest](../../src/test/java/br/com/imarui/ima/identity/feature/QueuedIdentityAdministrationTest.java) |
| Convite, administração e senha após revogação | [QueuedAuthenticationAdministrationTest](../../src/test/java/br/com/imarui/ima/authentication/feature/QueuedAuthenticationAdministrationTest.java) |
| Enforcement direto, DENY, anônimo, outro titular e Scope irmão | [ModuleUseCaseAuthorizationTest](../../src/test/java/br/com/imarui/ima/identity/feature/ModuleUseCaseAuthorizationTest.java) |
| Nome persistido de Tenant | [TenantHttpContractTest](../../src/test/java/br/com/imarui/ima/identity/api/contract/tenant/TenantHttpContractTest.java) |

Os testes usam unicamente o profile test e credenciais sintéticas de container. Falhas SQL,
dependência e SMTP são controladas/injetadas, não interrupções de sistemas reais.

## 12. Jira e pendências

Consultado **Atlassian Rovo Legacy**, com leitura única do índice e consulta dirigida aos critérios:

| Issue | Relação e tratamento |
| --- | --- |
| [IIAMS-23](https://logimarui.atlassian.net/browse/IIAMS-23) | G-05 e lifecycle de Tenant; registrar evidência sem reabrir/encerrar arbitrariamente |
| [IIAMS-54](https://logimarui.atlassian.net/browse/IIAMS-54) | G-02/G-04: recuperação, troca e prova da credencial |
| [IIAMS-63](https://logimarui.atlassian.net/browse/IIAMS-63) | G-01: administração de sessões/refresh com ator vigente |
| [IIAMS-71](https://logimarui.atlassian.net/browse/IIAMS-71) | G-01/G-02: interoperabilidade de revogação e worker atrasado |
| [IIAMS-38](https://logimarui.atlassian.net/browse/IIAMS-38) | Backlog administrativo futuro: preservado, sem declarar CRUD/filtragem/novas regras implementadas |
| [IIAMS-151](https://logimarui.atlassian.net/browse/IIAMS-151) | PR #4 integrado e entrega local/CI comprovada; descrição exige continuar aberta pelas dependências externas |
| [IIAMS-152](https://logimarui.atlassian.net/browse/IIAMS-152) | Continua aberta: coletor e entrega externa real de alertas sem comprovação operacional |
| [IIAMS-153](https://logimarui.atlassian.net/browse/IIAMS-153) | Continua aberta: retenção, custódia, RPO/RTO e recuperação real sob governança |

Sem nova Task para defeitos concluídos nesta execução; sem duplicar as pendências já existentes.
O registro de evidência usa comentários com HEAD, testes e link de PR nas issues relacionadas. Nenhuma mudança de Sprint,
aceite formal ou fechamento de Epic integra esta entrega.

## 13. Commits e entrega

| Commit | Conteúdo |
| --- | --- |
| `9debc4f` | Reproduções iniciais antes das correções |
| `0d4b69260a6a7682704f3c3451e83f6675073f26` | Correções G-01–G-05, ports de acesso e regressões/fixtures explícitas |
| Consolidação documental posterior | Este relatório, matriz de testes e evidências operacionais; sem alteração de código |

Branch publicada e PR novo para `main`, sem merge automático. O HEAD do PR inclui a consolidação
documental posterior ao HEAD de código acima e deve ser conferido pelos checks remotos.

## 14. Parecer técnico e limites

Cinco findings comprovados, dois HIGH e três MEDIUM, tratados diretamente no aplicativo.
Sem CRITICAL demonstrado nesta revisão. **Parecer técnico favorável à revisão humana do PR**,
com regressão local completa aprovada e os limites operacionais abaixo preservados.
Aceite formal, aprovação e decisão de merge permanecem humanos; CI é conferido no HEAD de entrega.

Riscos não demonstrados como defeitos: capacidade sob tráfego real, ambiente HTTPS/cookies/SMTP,
custódia/rotação real de chaves, stack de alertas e recuperação de produção. Permanecem distintos
dos bugs reproduzidos e das decisões arquiteturais intencionais acima.
Esta auditoria não equivale a pentest de infraestrutura real nem garante ausência de todo defeito.
