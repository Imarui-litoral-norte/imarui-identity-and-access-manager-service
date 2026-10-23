# Authorization — hardening operacional (Rodada 4)

## Baseline e limites

Default real: `main`. PR #3 integrado em 2026-10-23, merge
`aaf04a575304b57b8e04d2520647037a0fba4f8a`, contendo o HEAD revisado
`745e68d96a58e8d24435de8990e2c9f6cba30203` sem diferença de conteúdo.
[CI da integração aprovado](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/actions/runs/40005922675).
Branch desta entrega: `chore/iiams-81-operational-hardening`, criada da default atualizada.
V1–V25 permanecem imutáveis. Sem alteração no PR #3, Gateway, produção, merge ou deploy.

## Checkpoints

1. Recuperadas as ressalvas de `ROUND_3_REVIEW.md`, arquitetura, operação e contratos.
   Consultados IIAMS-128/133/143 pelo Atlassian Rovo Legacy. Busca por Tasks existentes
   não encontrou hardening equivalente (somente IIAMS-80, fora do escopo).
2. Baseline: `mvnw.cmd -B clean verify`, Java 17.0.18/PostgreSQL 18.4, 347 testes,
   42 suites, sem falhas/erros/ignorados. Task técnica criada no Legacy:
   [IIAMS-151](https://logimarui.atlassian.net/browse/IIAMS-151). A primeira criação
   expirou; a busca subsequente não encontrou duplicata e a segunda foi confirmada.
3. Benchmark em schema novo por cenário: 51/151/301 Identities, 33/83/163 Scopes,
   DENYs e delegações, quatro leitores PDP com três escritas simultâneas, reparenting
   e revogação. [Dados comparativos](round-4-benchmark.json). Reutilização de caminhos
   imutáveis dentro da árvore da revisão reduziu a mediana da edição compartilhada
   no maior cenário de 652,2181 para 445,6952 ms (31,7%). Cenários menores variaram;
   três amostras de mutação não demonstram p99, capacidade de produção ou SLA.
   Retenção de caminhos limitada a 100 mil referências por árvore; árvores excepcionalmente
   profundas continuam validadas e usam o percurso original após esse orçamento, sem crescimento
   quadrático de referências retidas. Não existe cache compartilhado entre revisões/requisições.
4. Sinais de replay, falha de auditoria, erro/timeout de dependência, espera por lock
   e mutações confirmadas. Falha de auditoria PDP agora registra duração ERROR,
   preservando resposta fail-closed e ausência de contador de decisão confirmada.
   47 testes dirigidos aprovados (HTTP/M2M, métricas protegidas, árvore e sinais).
5. Capacidade de auditoria: 40 mil eventos, 100 consultas paginadas por Scope, p50
   2,2192 ms / p95 3,9679 ms / p99 6,2018 ms. Planos usam índices existentes.
   Aproximadamente 16,27 MB físicos incluindo índices para este payload curto; não é
   estimativa de tamanho de eventos de produção. Append-only preservado, sem V26.
6. Ensaio PostgreSQL 18.4: backup custom V20 populado, SHA-256, upgrade V25, falha SQL
   controlada/rollback, restore em outra base, validate V20 e upgrade V25 novamente,
   fingerprints de 12 tabelas preservados. ROOT e imutabilidade continuam protegidos.
   [Evidência](round-4-recovery-rehearsal.json). O preflight foi executado com sucesso no build final.

## Artefatos operacionais

- [Medições comparáveis F-02](round-4-benchmark.json): melhoria localizada, sem cache
  entre decisões, invalidação global ou alteração do avaliador. O maior cenário continua
  mostrando custo de mutação crescente; F-02 é tratado/medido, não encerrado como capacidade ilimitada.
- [Sinais e regras de alerta](MONITORING.md): integração preparada, sem coletor ativo.
- [Auditoria e retenção](AUDIT_RETENTION.md) e [dados/planos](round-4-audit-capacity.json):
  índices suficientes no ensaio; retenção, custódia e eventual arquivo externo dependem de decisão.
- [Runbook de banco](DATABASE_RUNBOOK.md) e [preflight](../../scripts/authorization/preflight.sql):
  somente leitura, inventário/checksums/locks; upgrade e recuperação com controles explícitos.

## Findings e decisões

| Item | Resultado |
| --- | --- |
| F-02 | Medido com cenários fixos; reaproveitamento de caminhos por revisão. Snapshot completo/global lock preservados; não define SLA de produção |
| Duração ALLOW em auditoria PDP falha | Corrigido: ERROR; decisão confirmada não é contada, resposta continua 503 |
| Replay sem sinal específico | Contador finito REPLAY e reason `REPLAY_DETECTED`; resposta genérica não expõe causa técnica |
| Falha de auditoria dependia do próprio banco para observação | Counter/log local sem SQL/segredos, erro repropagado e rollback preservado |
| Lock e mutações confirmadas | Tempo de espera observável; attempts distinto de afterCommit, inclui revogação técnica |
| Índices/contratos novos | Descartados sem gargalo demonstrado; consultas e contratos atuais preservados |
| Limpeza/TTL de auditoria | Não implementada: falta política aprovada, integridade/históricos preservados |
| Alertas e custódia externa | Especificação/runbook prontos; ativação operacional pendente, sem fingir implantação |

## Leitura das medições F-02

| Scopes / Identities / assignments | Snapshot p95 antes → depois (ms) | Edição compartilhada p50 antes → depois (ms) | PDP concorrente p95 depois (ms) | Throughput observado depois (decisões/s) |
| --- | --- | --- | --- | --- |
| 33 / 51 / 51 | 3,9728 → 5,2251 | 26,8015 → 30,9094 | 51,4348 | 210,48 |
| 83 / 151 / 151 | 5,0808 → 4,5698 | 127,6990 → 110,1325 | 21,9534 | 348,69 |
| 163 / 301 / 301 | 3,3623 → 4,1649 | 652,2181 → 445,6952 | 21,2349 | 91,10 |

Um processo por variante, cenários executados na mesma ordem; aquecimento de JVM/host não
é totalmente controlado. Variações mistas do snapshot e cenário pequeno são registradas,
sem afirmar aceleração geral. Throughput considera a janela completa, inclusive cauda das
três escritas; não é taxa máxima sustentada de leitores. O pipeline mede PostgreSQL,
snapshot/evaluator, binding e journal reais com contextos humano/técnico pré-validados;
não mede RSA, HTTP/rede nem toda infraestrutura. O contrato HTTP/M2M tem regressão separada.
100 amostras de snapshot/árvore, 1.000 avaliações, 120 PDP; p99 só publicado para n≥100.
Mutações: 3 edições, 4 reparentings, 1 revogação, sem p99. Nova decisão após commit de
revogação obrigatoriamente nega. Os 4.687 Scopes do benchmark histórico acumulado não são
equivalentes a estes cenários, nem autorizam extrapolação linear de capacidade de produção.

O ganho medido motiva somente a reutilização local de caminhos. Carregamento completo,
loops de impacto e serialização de mutações continuam limites reais de escala. Capacidade
alvo, profundidade/forma das árvores, distribuição de assignments, payloads e concorrência
do ambiente ainda exigem teste de carga autorizado e orçamento do PEP. Não foi criado cache
de ALLOW, indexação complexa de regras ou outro algoritmo de decisão.

## Dependências no Jira Legacy

- [IIAMS-151](https://logimarui.atlassian.net/browse/IIAMS-151): checkpoint da implementação e evidências desta rodada.
- [IIAMS-152](https://logimarui.atlassian.net/browse/IIAMS-152): escolher/configurar coleta protegida e testar entrega/escalonamento de alertas.
- [IIAMS-153](https://logimarui.atlassian.net/browse/IIAMS-153): aprovar retenção/capacidade/custódia, RPO/RTO e recuperação do ambiente real.

Tasks vinculadas ao Epic e mantidas abertas. Nenhuma Story foi reaberta, Sprint alterada
ou aceite formal do Epic declarado. IIAMS-128/133/143 foram confrontadas com o código e
receberão os links de evidência desta entrega. A criação inicial expirada não gerou duplicata;
busca posterior confirmou somente as três Tasks acima.

## Estados de entrega

| Estado | Evidência / limite |
| --- | --- |
| Implementado e validado | Caminhos por revisão com memória limitada; métricas e logs sem segredos; replay/audit failure; testes de métricas autenticadas; preflight; backup/restore sintético V20→V25; regressão completa |
| Preparado para integração futura | Catálogo de sinais, critérios/severidades de alertas, runbook de coleta, backup e restauração |
| Depende de decisão operacional | Coletor/autenticação/destinos de alertas; retenção/hold/custódia; RPO/RTO; capacidade e orçamento real do PEP |
| Não testado | Infra de produção, carga real sustentada, entrega externa de alerta, criptografia/backup remoto/PITR e restauração de produção; runner Docker Windows não executado localmente |
| Risco residual | Snapshot cresce com política; loops de impacto/shared Role e global lock limitam escala; OFFSET/count de auditoria custam em grandes fronteiras; journal cresce sem retenção aprovada |

## Validação e entrega

Gate final local: **354 testes, 46 suites, 0 falhas, 0 erros e 0 ignorados**,
`mvnw.cmd -B clean verify` em Java 17.0.18/PostgreSQL 18.4. Código
`81dc5413b09848f15c12cb0aefc90554abd9f97e`; [resultado por suite](round-4-test-results.json).
Sete testes adicionais frente aos 347 do baseline. R3-01/R3-06/R3-08, DENY, delegação,
self-grant, ownership, isolamento de Tenants, ROOT, sessões/revogação, M2M/anti-replay,
PDP/PEP fail-closed, atomicidade de auditoria, clean install e upgrades antigos permanecem verdes.
O teste de árvore profunda também cobre o fallback após esgotar o orçamento de caminhos.

O PR novo é destinado a `main`; seu corpo/checks registram o CI do HEAD publicado, incluindo
o caminho Docker com clientes do PostgreSQL do CI. Esta evidência local não substitui esse gate.
Não há novas migrations, mudança de contrato HTTP, funcionalidades de Authorization, Gateway,
merge, deploy ou alteração de produção. PR #3 permanece integrado e inalterado.
Parecer desta rodada: hardening técnico validado no ambiente isolado; preparo de deploy depende
das pendências operacionais explícitas. Não confundir PR revisável com autorização de implantação.
