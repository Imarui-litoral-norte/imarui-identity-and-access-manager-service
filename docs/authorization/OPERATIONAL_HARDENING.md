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
4. Sinais de replay, falha de auditoria, erro/timeout de dependência, espera por lock
   e mutações confirmadas. Falha de auditoria PDP agora registra duração ERROR,
   preservando resposta fail-closed e ausência de contador de decisão confirmada.
   47 testes dirigidos aprovados (HTTP/M2M, métricas protegidas, árvore e sinais).

## Estados de entrega

Evidências implementadas/validadas, preparação de integração, decisões operacionais pendentes,
itens não testados e riscos residuais serão distinguidos nos checkpoints finais deste documento.
Não há stack de monitoramento definida nos arquivos operacionais examinados. Não se presume
coletor, sistema de alertas, política de retenção ou backup de produção validado.
