# Authorization IIAM — revisão corretiva da Rodada 3

Data do parecer: 2026-10-23. Esta revisão continua o trabalho de 2026-10-22.

Base auditada: `a994d58f3a4673de864bf09d66c7733bd8409de1`, PR #3 / branch
`feat/iiams-81-authorization-foundation`, main `f419a501328774fefec24b6ac28070e19f972cf8`.
Checkout limpo e sincronizado no início; PR aberto, Draft, mergeable, sem conflito ou revisão
corretiva anterior identificada em commits/documentos/comentários do PR. Histórico preservado.
Sem alteração no Gateway, merge, deploy ou operações em produção.

## Baseline e fontes

Os seis documentos prioritários foram lidos. Novo `mvnw.cmd -B clean verify` na base:
320 testes, 0 falhas/erros/ignorados, JAR gerado, Java 17.0.18 / PostgreSQL 18.4 isolado.
Não se usa o CI antigo como substituto da validação desta rodada.

Consultadas somente IIAMS-128, IIAMS-133 e IIAMS-143 diretamente pelo Rovo Legacy;
critérios recuperados, sem timeout. As três continuam formalmente A fazer. Matriz de 58
subtarefas da Rodada 2 é contexto, não nova implementação ou aceite formal.

## Findings reproduzidos e correções

| ID | Severidade | Reprodução na base | Correção / regressão |
| --- | --- | --- | --- |
| R3-01 | Alta, bloqueante | ROOT tem DENY por Role sobre Permission externa que recebe por outra Role. Transferir ROOT remove a Role negadora; iniciador passa de DENY a ALLOW. Teste falhou com `expected false but was true` | Transferência inclui Permissions dos DENYs ativos de ROOT na fronteira de preservação/checagem de ganhos, além de sua composição. `transferCannotRemoveARootRoleDenyForAPermissionOutsideRootComposition` |
| R3-02 | Média | PEP com timeout 200 ms recebe headers imediatos e corpo ALLOW atrasado. A chamada excedeu 900 ms; timeout anterior não abrangia o corpo | Future com deadline até corpo completo e cancelamento; subscriber limita bytes recebidos a 16 KiB antes de JSON. `deadlineAlsoCoversTheResponseBodyAndCannotAcceptALateAllow`, `oversizedBodyIsRejectedBeforeJsonParsing` |
| R3-03 | Média, integridade | UPDATE persistido ACTIVE→PENDING remove o último ROOT ativo sem erro: V21 só protege DISABLED/delete. A API não oferece essa transição, mas a invariante do banco era incompleta | Nova V25 substitui somente a função de guarda; qualquer saída de ACTIVE mantém a proteção. `lastActiveRootCannotBeDemotedToPendingThroughAPersistedLifecycleWrite` |
| R3-04 | Média, disponibilidade | Transação bloqueia um nonce expirado. Prova nova independente bloqueia no DELETE de limpeza e excede 2 s | Limpeza de até 256 expirados com `FOR UPDATE SKIP LOCKED`, usando índice existente; unicidade/rejeição de prova expirada preservadas. `anUnrelatedLockedExpiredNonceCannotBlockFreshAuthentication` |
| R3-05 | Média, observabilidade | Critério IIAMS-133 exige alertas. Havia auditoria ROOT persistida, porém nenhum sinal dedicado de transferência para o operador; teste de sinal após commit retornou contador 0 | WARN estruturado e `iiam.root.transfers` com apenas REQUESTED/CONFIRMED após commit. Rollback e confirmação repetida não produzem confirmação extra. Contexto humano rejeitado no PDP também tem evento correlacionado, sem registrar token/Actor não autenticado |
| R3-06 | Alta, bloqueante | Sujeito sem ALLOW possui delegação granular bloqueada por DENY ancestral. Reparenting remove a negação e restaura delegação; teste esperava SecurityException, mas a alteração era aceita | Guard de impacto compara uso, delegação e redelegação com o evaluator único; inclui sujeitos com somente delegação, inclusive inativos na análise potencial. Self-grant indireto é rejeitado e restauração de autoridade alheia exige capacidade de redelegar. Regressões de reparenting, revogação de DENY e inativação de Role negadora |
| R3-07 | Baixa, contrato temporal | Prova RS256 válida com iat futuro dentro da tolerância e exp anterior a iat recebia HTTP 200 | Validade exige exp > iat. Teste inclui algoritmo HS256, typ humano e kid de outro serviço, que são rejeitados. Não se identificou ganho de autorização por esse defeito temporal |
| R3-08 | Alta, bloqueante | Comando administrativo autentica no filtro e aguarda lock 810082. Sessão é revogada antes de liberar o lock, mas criação de Role ainda retorna 201. Teste HTTP esperava 401 | AuthorizationActorAdapter revalida o JWT/SID usando o port de contexto humano existente no ponto de uso, após lock nas mutações e dentro do snapshot nas consultas. Comando enfileirado passa a negar sem criar Role; sem novo algoritmo ou substituição da autenticação |

Checkpoint após correções: 51 testes focados de PEP/ROOT/M2M/upgrade aprovados, zero falhas,
erros ou ignorados. Inclui falha de conexão técnica injetada retornando 503 e JWT humano
expirado/prova técnica como token humano retornando 401. V1–V24 permanecem intocadas.

O limite da limpeza é garantido por CTE `MATERIALIZED`: um teste com 600 expirados e um nonce
vivo verificou exatamente 256 remoções e preservação da barreira ainda válida. Uma subconsulta
não materializada permitia reavaliação pelo plano PostgreSQL; o teste detectou e a implementação
final corrigiu esse detalhe. Não há novo worker, cache ou mudança no protocolo de replay.

## Confronto com as três subtarefas prioritárias

| Issue | Critério recuperado no Legacy | Parecer técnico |
| --- | --- | --- |
| IIAMS-128 | Timeout, 401/403, correlação, métricas/logs, retries conservadores, batch e overload; sessão/revogação, anti-spoof, Tenant irmão e fail-closed | R3-02/R3-04/R3-07 corrigem defeitos reais. PEP tem deadline de corpo e limite de bytes; replay concorrente tem um vencedor; batch misto mantém resultado por item e falha estrutural reverte decisões anteriores. Não há retry automático ou cache permissivo. Capacidade de produção continua limite operacional medido |
| IIAMS-133 | Reautenticação reforçada, trilha/alertas/reason/runbook, último administrador, duas identidades, concorrência, DENY comum e rollback | R3-01/R3-03/R3-05 corrigidos. Duas reautenticações/sessões, expiração, confirmação repetida, destinatário desativado, iniciador revogado, composição modificada, falha parcial e timeout estão testados. Não foi exigido endpoint anônimo de emergência; restore de infraestrutura permanece procedimento operacional controlado |
| IIAMS-143 | Latência, motivos de DENY, mutações/correlação/PII/rate limit; consistência com JWT anterior, atomicidade e auditoria | Journal PDP no mesmo commit, eventos mínimos e consultas paginadas protegidas, sinais ROOT e rejeição de Actor registrados. Métricas têm labels de cardinalidade fixa. Instalar coletor/alertas externos e dimensionar retenção é operação; não equivale a ausência dos sinais no aplicativo |

## Auditoria técnica e superfície coberta

### PDP, M2M e consumidor

Foram inspecionados PdpUseCases, TechnicalServiceAuthenticationAdapter, ServiceApiSecurityConfiguration,
ServiceAuthorizationController, TechnicalCallerAdapter, AuthorizationHumanContextAdapter,
JdbcServiceResourceRepository e AuthorizationPepClient. Assinatura RS256 tem algoritmo e typ fixos;
iss/sub/kid são vinculados ao cliente persistido e audience à instância destinatária configurada.
JWK/jku/crit não são fontes de chave. Rotação/revogação, caller→owner e operações são revalidados
no snapshot da decisão ou após lock do catálogo. Tokens humanos/técnicos não são intercambiáveis.

Método/path/digest dos bytes UTF-8/correlação são assinados; query string, charset alternativo,
compressão e Authorization duplicada são recusados. Proxy deve conservar o path canônico recebido
pelo IIAM e a audience configurada; headers Forwarded/X-Forwarded-* não concedem jurisdição.
Não se normaliza um Scope fornecido pelo frontend para convertê-lo em autoridade.

TENANT usa o binding oficial; objeto externo exige binding previamente autorizado e imutável,
enquanto o PEP resolve a propriedade real em seu banco. Acesso de A a Permission de B, kid alheio,
Actor trocado, Scope incorreto, Tenant irmão, JWT expirado/revogado, chave revogada, replay paralelo,
falhas de conexão/auditoria e resposta PEP tardia/malformada são exercitados. Falha de conexão
é injetada na dependência técnica; não se afirma ter derrubado banco de produção ou rede real.

O cadastro de objeto opaco não inventa um fluxo de transferência de propriedade: binding não
pode ser sobrescrito silenciosamente. Consumidor precisa resolver novamente a propriedade real;
recurso removido/desativado em sua base é rejeitado pelo resolver. Se usar registro opaco no IIAM,
mudança de propriedade exige procedimento revisado antes de reutilizar esse identificador.
O IIAM não tem como verificar autonomamente o banco privado de um consumidor desonesto.

### Regras, Scopes, ownership e APIs

Role GLOBAL é disponibilidade e exige assignment; ALLOW só desce na árvore; irmãos/raízes
são isolados. SCOPE/TENANT respeitam proprietário e disponibilidade. Affiliation não participa
da decisão. DENY de Identity/Role efetiva precede todo ALLOW, inclusive ROOT.
Uso, concessão e redelegação continuam direitos distintos. R3-06 fecha a lacuna de impacto
que não considerava restauração de delegação sem ganho de uso; não introduz outro evaluator.
R3-08 evita usar sessão apenas validada antes da fila de escrita: o guard verifica o contexto
humano novamente depois do lock, pelo decoder stateful e armazenamento de Authentication
existentes. O teste confirma HTTP 401 e ausência de Role persistida depois da revogação.

AuthorizationAdminController, TechnicalClientController, RootRecoveryController,
SecurityAuditController e CatalogInspectionController foram conferidos até os use cases/SQL.
Operações verificam ator atual, capability, fronteira, owner, delegação, DENY/lifecycle e versão
quando aplicável. Administrador local autenticado é testado contra gestão ancestral, Tenant irmão,
catálogo/clientes globais, composição/diagnósticos e consultas não autorizadas. Rotas de criação
sem recurso prévio não usam versão fictícia; revogação/edição de existentes revalida sob lock.

DEPRECATED mantém uso dos grants anteriores e recusa nova distribuição, conforme teste explícito
da Rodada 1; isso não é tratado como revogação. Remoção de RolePermission, assignment, DENY e
lifecycle aplicam revogação na política viva. Estado operacional de um Tenant não desativa
automaticamente outros Tenants descendentes; os Scopes administrativos têm lifecycle próprio.
Essas decisões aprovadas foram preservadas, não reclassificadas como bugs.

### ROOT, auditoria e concorrência

Transferência requer ROOT na fronteira exata e dois sujeitos ativos/reautenticados, com sessões
atuais, aprovação expirada rejeitada e consumo único. `preserveDenials` agora cobre também
Permissions apenas negadas por ROOT e ganhos exclusivos de delegação/redelegação, sem estender
o envelope mínimo ou conceder bypass de negócio. Fronteiras de negação são preservadas de cima
para baixo; novos DENYs só surgem quando necessários para impedir ganho antes bloqueado.

Testes verificam negação direta/herdada, com e sem grant de uso, concorrência entre transferências,
desativação do destinatário, revogação do iniciador, composição alterada, duplicação, timeout do
lock e falha de auditoria no meio/final das alterações de delegação. O approval permanece pendente
e nenhuma concessão parcial sobrevive a rollback. Sinais de confirmação só são emitidos após commit.

As leituras REPEATABLE READ e o lock 810082 foram preservados. Um teste prende resolução do recurso
depois de preparado o snapshot, confirma um DENY em outra transação e libera a primeira decisão:
a avaliação já iniciada usa a revisão anterior, a próxima com o mesmo JWT nega. Não há cache
entre decisões nem promessa de atomicidade distribuída com a transação do consumidor.

PDP ALLOW/DENY exigem evento com mesmo decisionId no commit; falha de journal devolve indisponibilidade.
Auditoria administrativa registra before/after, ator, serviço, Scope/recurso, motivo/instante/correlação.
Consultas de auditoria são paginadas, filtradas por jurisdição e incluem histórico de Scopes excluídos;
eventos globais e Actuator administrativo exigem auditoria PLATFORM. Não há secrets ou PII em labels
de métricas. Tokens/senhas são WRITE_ONLY/redigidos em DTOs e não constam dos eventos acrescentados.

### Migrations e findings anteriores

V18: reconciliação sem apagar histórico e índices únicos de DENY por destinatário.
V19: status/boolean compatíveis, archive terminal e exclusão com histórico independente.
V20: FK/UUID/nonce por chave, jurisdições e credenciais públicas protegidas.
V21: histórico append-only/transferência, com a lacuna de status corrigida em V25.
V22: índices operacionais/correlação. V23: upgrade explícito interno, sem ressuscitar delegação
historicamente revogada. V24: criação/reativação de assignment exige Role ACTIVE sob lock.

V1–V24 são preservadas byte a byte. V25 não altera dados, IDs, grants ou históricos; muda a
função da guarda existente. Instalação limpa, upgrades V10/V17/V20 populados e V24→V25 têm
testes reais PostgreSQL. O novo teste V24 verifica IDs/grants preservados e ausência de delegação
automática. Constraints/FKs/triggers e mapeamentos JPA/JDBC são validados pelo build completo.
Não se reexecutam migrations editadas, limpa schema operacional ou afirma down migration.

F-03 revalidado: unicidade/idempotência/concorrência e nova geração após revogação continuam.
F-04 revalidado: ACTIVE/INACTIVE/ARCHIVED e exclusão histórica continuam.
F-05 revalidado: catálogo e PDP usam o mesmo mecanismo técnico e owner/revogação funcionais.

## F-02 e limites operacionais

Baseline desta revisão: 3.286 Scopes/251 Permissions, 301 Identities, 41 Roles, 901 assignments;
snapshot p95 12,8828 ms, árvore p95 0,7687 ms, avaliação preparada p95 0,005 ms e uma edição
compartilhada 1.216,7099 ms. A medição antiga de 2.365 Scopes/172 Permissions tinha outro volume.
O teste sintético e a implementação de snapshot/global lock foram mantidos. Não comparar
valores de fixtures acumuladas como benchmark controlado de capacidade de produção.

Guard de impacto acrescenta a verificação obrigatória de delegação, e `mayDelegate` recusa cedo
ausência de autoridade antes de calcular Roles efetivas, preservando o mesmo resultado lógico.
Não há cache ou índice novo. A medição final e seus volumes estão em
[round-3-benchmark.json](round-3-benchmark.json); baseline e resultado são registrados separados.
Crescimento do snapshot, checagem de impacto compartilhado, serialização de escritas e retenção
de auditoria continuam pontos de monitoração, não bloqueios sem impacto adicional comprovado.

## Validação final e parecer

**347 testes em 42 suites, 0 falhas, 0 erros e 0 ignorados**, `mvnw.cmd -B clean verify`,
JAR gerado. São 27 casos adicionais em relação ao baseline de 320. Local: Java 17.0.18 e
PostgreSQL 18.4 isolado. Código validado: `eea59130253b94530e4653f636ce53a128cabafe`.
[CI aprovado](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/actions/runs/39953668623)
no mesmo código: Java 17, PostgreSQL 17.11, 347 testes sem falhas/erros/ignorados.
[Resultado estruturado por suite](round-3-test-results.json). A consolidação documental posterior
não altera o código validado; o check do HEAD de entrega é conferido novamente no PR.

Medição final isolada: 4.687 Scopes, 445 Permissions, 301 Identities, 41 Roles e 901 assignments;
snapshot p95 **16,6587 ms**, árvore p95 **0,9488 ms**, avaliação preparada p95 **0,0095 ms**,
uma edição compartilhada **1.671,8446 ms**. O conjunto acumulado é maior que o baseline;
não é comparação A/B de dados idênticos. Não houve regressão bloqueante demonstrada no cenário
ensaiado; carga de produção e contenção sustentada continuam não verificadas.

**Parecer técnico favorável à integração do PR #3 após revisão humana.** Os oito findings
identificados estão corrigidos e cobertos; R3-01, R3-06 e R3-08 eram bloqueantes e não permanecem
abertos. Não foi identificado impedimento estrutural remanescente de autorização/integridade
nos caminhos auditados. Main continua a base e não há conflito de merge no checkpoint conferido.

Ressalvas não bloqueantes: F-02 (crescimento de política/serialização e capacidade de produção),
integração do coletor/alertas e retenção de auditoria, preflight/backup/restauração sob governança.
São responsabilidades operacionais explicitadas, não caminhos permissivos de autorização.
Um ambiente V24 já corrompido precisa diagnóstico e recuperação controlada: V25 não inventa
administrador, reativa sujeitos nem repara dados retroativamente. Aceite formal de Jira,
aprovação humana e decisão de merge continuam externos a este parecer.

Revisão de aplicativo/contratos e banco isolado: não é pentest de infraestrutura de produção,
ensaio de backup real, certificação formal ou garantia de ausência de todo defeito possível.
Não foram criadas novas Stories nem alterado o aceite formal do Epic. Integração continua humana;
esta rodada não executa merge, deploy ou mudanças do Gateway.

## Fontes executáveis principais

| Área | Implementação | Regressão |
| --- | --- | --- |
| ROOT e preservação de negações | [RootRecoveryUseCases](../../src/main/java/br/com/imarui/ima/authorization/core/application/usecase/RootRecoveryUseCases.java) | [RootRecoveryIntegration](../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationRootRecoveryIntegrationTest.java) |
| Impacto de uso/delegação | [AuthorizationMutationGuard](../../src/main/java/br/com/imarui/ima/authorization/core/application/service/AuthorizationMutationGuard.java) e [evaluator único](../../src/main/java/br/com/imarui/ima/authorization/core/domain/policy/EffectiveAuthorizationEvaluator.java) | [AdministrationIntegration](../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationAdministrationIntegrationTest.java) |
| Contexto humano depois do lock | [AuthorizationActorAdapter](../../src/main/java/br/com/imarui/ima/authorization/infra/security/AuthorizationActorAdapter.java) | [AdminHttpIntegration](../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationAdminHttpIntegrationTest.java) |
| M2M, nonce e temporalidade | [TechnicalServiceAuthenticationAdapter](../../src/main/java/br/com/imarui/ima/authentication/infra/security/TechnicalServiceAuthenticationAdapter.java) | [ServiceIntegration](../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationServiceIntegrationTest.java) |
| Deadline e limite de corpo no PEP | [AuthorizationPepClient](../../src/main/java/br/com/imarui/ima/authorization/api/pep/AuthorizationPepClient.java) | [PepClientTest](../../src/test/java/br/com/imarui/ima/authorization/core/AuthorizationPepClientTest.java) |
| Integridade incremental | [V25](../../src/main/resources/db/migration/V25__protect_active_root_lifecycle.sql) | [UpgradeIntegration](../../src/test/java/br/com/imarui/ima/authorization/infra/AuthorizationUpgradeIntegrationTest.java) |
