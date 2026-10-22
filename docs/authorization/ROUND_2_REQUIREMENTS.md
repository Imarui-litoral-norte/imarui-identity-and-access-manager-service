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
