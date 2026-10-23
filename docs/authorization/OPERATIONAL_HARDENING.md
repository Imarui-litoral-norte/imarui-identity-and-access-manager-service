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
   [Evidência](round-4-recovery-rehearsal.json). O preflight será revalidado no build final.

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

## Estados de entrega

Evidências implementadas/validadas, preparação de integração, decisões operacionais pendentes,
itens não testados e riscos residuais serão distinguidos nos checkpoints finais deste documento.
Não há stack de monitoramento definida nos arquivos operacionais examinados. Não se presume
coletor, sistema de alertas, política de retenção ou backup de produção validado.
