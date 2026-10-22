# Rodada 1 — requisitos consolidados e estado de execução

Fonte desta rodada: anexo **IIAM Authorization | Rodada 1 de 2**, recebido em 2026-10-22.
Este plano substitui a execução integral do Epic em uma única rodada. O objetivo atual
é IIAMS-82–87 e IIAMS-93–123, com publicação da branch e `ROUND_1_HANDOFF.md`.
IIAMS-88–92, Gateway, PR final do Epic, merge e deploy estão fora desta rodada.

## Leitura do Jira e limites da evidência

Usar exclusivamente o Rovo Legacy do LogImarui. A leitura individual do Epic nesta
rodada expirou; a leitura individual da Story IIAMS-82 também expirou. As tentativas
foram limitadas a essas duas chamadas nesta retomada, sem loop de leitura.
O plano novo autoriza seguir os requisitos recuperados, com retry limitado e declaração
das limitações. O anexo fornece requisitos detalhados para as seis Stories, mas não
substitui a verificação das descrições individuais das subtarefas no Jira.

As evidências de código/teste serão associadas a cada Story e a seu grupo de subtarefas.
Enquanto não houver acesso às descrições individuais, nenhuma subtarefa será declarada
completamente validada contra o Jira. Não serão inventados títulos ou critérios ausentes.

## Requisitos por Story

| Story | Subtarefas | Requisitos da fonte recuperada | Estado inicial |
|---|---|---|---|
| IIAMS-82 | 93, 94, 95, 96, 97 | Scope independente de Tenant; UUID, código canônico único, nome/status; múltiplas raízes; árvore íntegra; consultas; reparenting autorizado, auditado e transacional; binding estável de Tenant | Domínio e persistência em implementação; administração e testes completos pendentes |
| IIAMS-83 | 98, 99, 100, 101, 102, 103 | Catálogo único federado; serviço/namespace; manifesto versionado idempotente; ownership e conflitos; último catálogo válido; descontinuação sem exclusão; registro não concede grants; nenhum endpoint externo inseguro | Pendente |
| IIAMS-84 | 104, 105, 106, 107, 108 | Role GLOBAL/SCOPE/TENANT; IDs preservados; unicidade por proprietário; disponibilidade separada de atribuição; composição compartilhada atualizada após commit; ROOT protegido | Pendente |
| IIAMS-85 | 109, 110, 111, 112, 113 | RoleAssignment Identity/Role/Scope; lifecycle; herança apenas descendente; sem exigir Affiliation; migração PLATFORM sem perda/ampliação a raízes independentes | Pendente |
| IIAMS-86 | 114, 115, 116, 117, 118 | DENY Identity ou Role, Permission e Scope; herança descendente; precedência absoluta; default deny; revogação, motivo/auditoria/concorrência; proteção mínima de recuperação | Pendente |
| IIAMS-87 | 119, 120, 121, 122, 123 | Delegação explícita separada de uso; limite por Permission/Scope; self-grant, ownership, alterações compartilhadas e reparenting protegidos; sem transitividade implícita; validação e escrita serializadas | Pendente |

## Decisões iniciais

- Reutilizar a auditoria e o commit `72f7749`, na mesma branch renomeada para
  `feat/iiams-81-authorization-foundation`. Base: `origin/main`, `f419a50`.
- Não alterar V1–V10. V11 acrescenta a árvore e o binding. Não representa nenhum
  agrupamento geográfico. A raiz administrativa `IIAM_PLATFORM` é a fronteira explícita
  de migração do grant PLATFORM existente; raízes independentes futuras não são suas filhas.
- Tenants existentes e futuros ganham um Scope próprio sob essa fronteira inicial,
  de forma transacional. O UUID do Tenant é preservado e não se torna o UUID do Scope.
  O binding não pode ser trocado nem removido. Reparenting altera apenas o parent do Scope.
- O estado operacional do Tenant continuará sendo consultado separadamente pelo avaliador;
  não se duplica seu lifecycle no Scope. Desativar um ancestral de Scope suspende sua subárvore.
- Consultas hierárquicas usam CTE recursiva, PK e índice de parent. O domínio usa mapa
  imutável e detecta ciclos/ancestrais ausentes; nenhuma autoridade é derivada de geografia.
- Mutações de política usarão o mesmo lock transacional PostgreSQL da hierarquia
  (`810082`). O objetivo é serializar validação e escrita entre instâncias; otimizações
  futuras não podem enfraquecer esse contrato.
- A raiz administrativa não pode ser desativada, reparentada ou excluída. Proteções de
  ROOT complementares serão implementadas nesta rodada; recuperação integral permanece IIAMS-89.

## Baseline e verificação incremental

O baseline anterior é [baseline-validation.md](baseline-validation.md): 191 testes,
duas falhas de agendamento imediato no PostgreSQL nativo/Windows. Os testes foram
ajustados para preparar jobs efetivamente vencidos antes de invocar o worker, sem alterar
backoff, regras de reserva ou código de produção de Authentication.

Os novos cenários serão executados por incremento, seguidos da regressão completa e
teste de upgrade populado. Resultados e commits serão registrados no handoff final.

### Primeiro incremento validado

`mvnw.cmd -B clean verify` em 2026-10-22, Java 17.0.18 e PostgreSQL nativo 18.4
isolado: **209 testes, zero falhas, erros ou ignorados; BUILD SUCCESS e JAR executável**.
São 18 cenários novos (5 de domínio Scope, 4 de persistência Scope, 3 de manifesto,
6 de integração do catálogo) sobre os 191 existentes. V11/V12 foram aplicadas em
uma base de teste V10 existente. A suíte também manteve os upgrades V5 e V9.

Esse resultado verifica os componentes já construídos, não encerra IIAMS-82/83:
administração autorizada de Scope, políticas integradas, testes adicionais de upgrade
de grants populados e a conferência de critérios individuais do Jira ainda permanecem.
O catálogo não publica endpoint externo; ausência de adapter técnico nega a chamada
externa interna, e os testes HTTP verificam 401 anônimo e 404 autenticado.

### Segundo incremento validado

V13 preserva os IDs de Role/Permission/RolePermission e converte cada vínculo antigo
Identity/Role em RoleAssignment UUID no Scope administrativo explícito. O nome da
tabela operacional é mantido por compatibilidade; o campo textual `scope` foi removido.
Roles agora têm ownership GLOBAL/SCOPE/TENANT e unicidade por proprietário, mantendo
a semântica anterior de nomes sensíveis a maiúsculas/minúsculas. O binding Tenant/Scope
é validado por FK composta. V14 acrescenta DENYs e delegações explícitas.

O domínio `IdentityRole` foi removido, substituído por `RoleAssignment`. O provider de
authorities antigo foi substituído pelo avaliador comum. APIs administrativas antigas
sem contexto continuam consultando exclusivamente a fronteira PLATFORM explícita;
grants de um Tenant não são unidos em uma authority global. A adaptação dos endpoints
que têm um Tenant como recurso ainda será feita antes da entrega.

`EffectiveAuthorizationEvaluator` aplica estado de Identity, Scope e Tenant; ownership;
assignments ativos; herança descendente; DENY Identity/Role absoluto; default deny.
Não consulta Affiliation nem claims de Permission. Uma delegação não concede uso;
uso não concede delegação; redelegação é uma flag explícita; Permission descontinuada
permanece utilizável por grants existentes, mas não pode ser novamente delegada.

`EffectiveAuthorizationUseCases` lê uma revisão consistente em REPEATABLE_READ,
sem cache de JWT. Identity publica somente projeções mínimas de IDs ativos, sem
duplicar entidades. A fixture de testes repõe o ROOT após o truncate descartável,
pois as novas FKs de ownership incluem Roles nesse grafo de limpeza.

Validação completa: **225 testes, zero falhas, erros ou ignorados; BUILD SUCCESS**,
Java 17.0.18/PostgreSQL 18.4. Novos 14 cenários do algoritmo e dois testes de migração
verificam instalação limpa e upgrade V10 populado, com IDs, grants, timestamps,
Identity, Tenant, Session e Refresh preservados. Flyway validate passou nos schemas.

Próximo trabalho obrigatório: mutações administrativas com ator confiável, delegação
por Permission/Scope, prevenção de self-grant/alterações compartilhadas/reparenting;
auditoria dessas mutações; proteção mínima ROOT/último administrador; enforcement
dos recursos Tenant nas APIs existentes; concorrência e testes de segurança reais.
Esses requisitos ainda não estão encerrados pelos testes de domínio acima.
