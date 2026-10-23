# Authorization IIAM — revisão corretiva da Rodada 3

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

Checkpoint após correções: 51 testes focados de PEP/ROOT/M2M/upgrade aprovados, zero falhas,
erros ou ignorados. Inclui falha de conexão técnica injetada retornando 503 e JWT humano
expirado/prova técnica como token humano retornando 401. V1–V24 permanecem intocadas.

## Parecer em elaboração

As correções acima estão implementadas e testadas no checkpoint; o parecer definitivo depende
dos cenários adversariais adicionais, regressão final e CI do novo HEAD. Não considerar este
checkpoint intermediário como aprovação para merge.
