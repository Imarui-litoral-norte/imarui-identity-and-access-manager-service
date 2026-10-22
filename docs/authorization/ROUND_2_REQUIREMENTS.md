# Authorization — Rodada 2

Base verificada: ef10547909aa9ee066957e649c1af6b1351ad34f; branch feat/iiams-81-authorization-foundation; PR #3/main.

Fontes: instrução integral do usuário de 2026-10-22 e Jira Rovo Legacy (LogImarui).
O Epic IIAMS-81 foi lido uma vez nesta rodada; o acesso voltou a funcionar. Critérios por Story/subtarefa são preservados em jira/*.json, sem metadados de conta/credenciais.

## Fases e checkpoints

1. Recuperar handoff e validar branch/base; executar baseline 262 testes.
2. F-03: DENY ativo equivalente idempotente, reconciliação auditada V18 e índices parciais; revogado não reativa implicitamente: nova criação gera novo ID.
3. F-04: lifecycle ACTIVE/INACTIVE/ARCHIVED; arquivamento terminal, sem assignments ativos; exclusão somente arquivada e sem assignments operacionais, com histórico independente.
4. IIAMS-88/F-05: mecanismo M2M único, credenciais assimétricas de cliente, prova por requisição (audience/issuer/nonce/exp/body), catálogo e PDP; sessão humana validada separadamente.
5. IIAMS-89/90: recuperação/transferência ROOT controlada e APIs protegidas; reutilizar guard/evaluator.
6. IIAMS-91: auditoria, explicações autorizadas, métricas e decisões consistentes; medir F-02 sem cache obsoleto.
7. IIAMS-92: upgrade V10/V17, regressão completa, documentação final e PR #3 consolidado.

Nenhuma alteração no Gateway; sem merge/deploy. Matriz final distinguirá implementação, ressalvas e aceite formal por issue. Não transicionar issues antes da verificação individual.

## Checkpoint F-03/F-04

Baseline reexecutado: 262 testes, clean verify/JAR aprovados (Java 17.0.18, PostgreSQL 18.4 isolado).
Após as correções: 42 testes dirigidos de administração/upgrade aprovados, sem falhas/erros/skips.
Incluem duplicação Identity/Role, idempotência, concorrência, revogação/nova geração, lifecycle, histórico,
proteção ROOT e upgrade V17 populado com duplicatas. A primeira execução encontrou somente ausência
de updated_at obrigatório na fixture SQL nova; a fixture foi corrigida e o conjunto passou.

V18/V19 foram aplicadas no cluster isolado e ficam imutáveis a partir deste checkpoint.
Arquivamento exige revogar assignments ativos; Role com qualquer assignment operacional (mesmo revogado)
não pode ser fisicamente excluída. Exclusão permitida para Role arquivada sem assignments, preservando
composição, DENYs e estado em authorization_roles__history; falhas revertem tudo.

## Checkpoint IIAMS-88 / F-05

18 testes de catálogo e HTTP M2M/PDP aprovados, sem falhas/erros/skips. Prova RSA por requisição
RS256, typ próprio, issuer/subject vinculados ao serviço, audience distinta, validade até 60 segundos,
jti UUID consumido no PostgreSQL, binding método/caminho/hash do corpo; chaves públicas revogáveis,
com rotação sobreposta e validade máxima de 90 dias. Nenhuma chave privada é gerada/recebida no IIAM.

O PDP verifica o JWT humano pelo decoder stateful existente, compara seu subject ao Actor e resolve
Tenant→Scope no binding persistido (ou recurso externo explicitamente cadastrado), verificando
jurisdição técnica e owner da Permission. Resposta comum não revela Roles/DENYs. Falha de auditoria
retorna indisponibilidade, nunca ALLOW. V20 foi aplicada e fica imutável neste checkpoint.

Manifesto interno passou à versão 2 pelos três novos códigos administrativos. V20 faz upgrade explícito
das delegações internas dos ROOT existentes; manifests externos continuam sem conceder direitos.
Ainda serão concluídos o contrato consumidor/timeout, diagnósticos e hardening/observabilidade.

## Checkpoint IIAMS-89/90

15 novos testes HTTP de administração e recuperação aprovados; os 39 casos de administração
da fundação também passaram na execução anterior. Scopes/Roles têm CRUD seguro, lifecycle,
composição, reparenting, precondições de versão no lock e exclusão apenas quando segura,
com histórico independente. Listagens paginadas, Roles disponíveis e diagnósticos exigem
AUTHORIZATION_AUDIT_READ no recurso; disponibilidade não concede gestão do proprietário.

ROOT: transferência exata de fronteira com dois sujeitos ativos, duas reautenticações por senha,
confirmação pelo destinatário em até 10 minutos e sessão do iniciador ainda válida; revalidação
de autoridade/delegação/impacto, consumo único, revogação da atribuição e delegações anteriores,
auditoria e concorrência. Não há endpoint genérico para conceder ROOT.
PENDING não substitui o último ROOT ACTIVE. Envelope mínimo agora inclui ROOT_RECOVERY
além das seis capacidades de administração da Rodada 1; DENY comum continua prevalecendo.

V21 foi aplicada e fica imutável. Auditorias e históricos de Role/Scope/catálogo são append-only.
O loop de delegação da transferência revelou reconstrução de árvore por célula e excedeu
o prazo de 10 segundos; a árvore é agora reutilizada somente dentro da revisão corrente.
A suíte ROOT de 8 casos passou em 3,196 segundos após a correção; nenhum cache entre
requisições ou relaxamento de revogação foi introduzido. Medição de carga F-02 continua pendente.

## Checkpoint IIAMS-91 / F-02

Passaram execuções dirigidas de 33 casos (ROOT/HTTP/PDP/PEP/concorrência) e 10 casos
(administração/auditoria/performance). Novo envelope mínimo explícito: 9 capacidades de
segurança, incluindo AUDIT_READ e SERVICE_MANAGE; operações de negócio continuam sujeitas a DENY.
Transferência preserva DENYs comuns que seriam contornados e admite sucessão somente na
raiz ROOT efetivamente governada, sem conferir privilégios PLATFORM a uma raiz independente.

Jornal de decisão e auditoria de consultas usam a mesma transação/conexão da avaliação;
negações são registradas após rollback. Reautenticação termina antes da transação de
transferência, evitando retenção de conexão enquanto aguarda a confirmação de credencial.
Há correlação assinada M2M→PDP→catálogo, logs sem tokens/PII, métricas, batch de um único Actor,
paginação de auditoria com hierarquia atual/histórica e históricos append-only.

F-02: medição local isolada de 2026-10-22 19:30, PostgreSQL 18.4/Java 17.0.18, 30 snapshots,
30 reconstruções e 100 avaliações: 2.365 Scopes, 301 Identities, 41 Roles, 901 assignments,
172 Permissions. Snapshot p50 10,8272 ms / p95 12,4119 ms; árvore p95 0,5342 ms;
avaliação com árvore preparada p95 0,0035 ms; uma edição compartilhada 887,035 ms.
Não é SLA/benchmark de produção. Mantidos snapshot amplo e lock 810082; sem cache entre chamadas.
V22/V23 foram aplicadas e ficam imutáveis. V23 corrige a primeira autoridade ROOT das
capacidades introduzidas entre migração legada e bootstrap, sem ressuscitar delegações revogadas.
