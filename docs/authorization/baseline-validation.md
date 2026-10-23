# IIAMS-81 — validação da base anterior à implementação

Data: 2026-10-22. Código validado: `f419a501328774fefec24b6ac28070e19f972cf8`.
As únicas alterações da branch nesta etapa são documentos desta auditoria.
Não há implementação nova de Authorization ou teste de aceite das 58 subtarefas.

## Ambiente e execução

- Windows; Microsoft OpenJDK 17.0.18.
- PostgreSQL nativo 18.4, cluster temporário próprio, acesso apenas por loopback,
  porta efêmera, banco/usuário de testes `identity_test`.
- Maven Wrapper do projeto, sem alterar pom, migrations ou configuração da aplicação.
- Servidor temporário encerrado após a execução. Nenhum banco operacional foi usado.

O runner usual `scripts/test-identity.ps1` não conseguiu iniciar seu container:
o pipe `dockerDesktopLinuxEngine` não existia. O Docker Desktop não foi iniciado
nem reiniciado. A alternativa utilizou os binários PostgreSQL já instalados.

Uma primeira tentativa de execução completa não conseguiu conectar porque o processo
PostgreSQL temporário havia terminado quando a sessão de shell terminou. Os 155 erros
de inicialização dessa tentativa não representam 155 defeitos de aplicação. O estado
terminal do servidor foi confirmado com `pg_ctl status`; a execução seguinte manteve
servidor e Maven na mesma sessão e concluiu todos os testes.

Com `JAVA_HOME` apontando para o JDK acima e `IDENTITY_TEST_DB_URL` apontando
para o banco temporário, os comandos executados foram:

```powershell
.\mvnw.cmd -B '-Dtest=*DomainTest,*LifecycleTest,*UseCaseTest,AffiliationValidityTest,JwtKeyRotationTest,SmtpChallengeMailTest,SwaggerOperationCustomizersTest,IdentityApplicationResponseTest' test
.\mvnw.cmd -B clean verify
```

## Resultados observados

| Execução | Testes | Falhas | Erros | Ignorados | Resultado |
|---|---:|---:|---:|---:|---|
| Recorte sem banco | 36 | 0 | 0 | 0 | BUILD SUCCESS |
| Suíte completa, PostgreSQL nativo | 191 | 2 | 0 | 0 | BUILD FAILURE |

Os totais da execução completa foram extraídos de 29 relatórios XML Surefire:
[baseline-results.json](baseline-results.json). São 189 testes sem falha, sem erros
de contexto na execução definitiva. O build completo **não passou** e a etapa
de empacotamento posterior aos testes não foi concluída nessa execução.

Falhas:

1. `AuthenticationIntegrationTest.notificationJobRetriesWithoutStoringRawSecret`,
   linha 357: o segundo `delivery.processOne()` retornou false.
2. `AuthenticationStabilizationIntegrationTest.notificationBackoffIsProgressiveAndReservationSurvivesWorkerRestart`,
   linha 368: `restarted.processOne()` retornou false.

Nos dois pontos, o teste atualiza `next_attempt_at=current_timestamp` e executa
imediatamente o worker. O worker compara esse campo com o Clock Java injetado.
Um diagnóstico JDBC separado, sem alterar o código do repositório, mediu 300
leituras de `clock_timestamp()` e comparou cada instante com `Clock.systemUTC()`
**depois** de receber a consulta: 294 leituras ainda estavam no futuro em relação
à JVM; diferença máxima de 9.922 microssegundos, mínima de -485 microssegundos.

Essa medição sustenta uma diferença de relógio/resolução no ambiente Windows nativo
como causa dos dois testes sensíveis ao agendamento imediato. Não houve alteração
do worker, antecipação de jobs de produção ou enfraquecimento da regra de backoff.
A futura correção deve tornar a preparação do teste determinística, mantendo
íntegras as garantias de retry, reserva e limite de tentativas. A confirmação em
PostgreSQL 17/Linux e a validação final do Epic continuam pendentes.

## Acesso aos requisitos formais

As seguintes operações do **Atlassian Rovo Legacy** retornaram
`Mcp error: -32603: MCP request timed out`:

- Pesquisa JQL de IIAMS-81 a IIAMS-150 usando o hostname do LogImarui.
- Leitura individual de IIAMS-81 usando o hostname do site.
- Listagem dos sites acessíveis pelo plugin.
- Nova leitura individual de IIAMS-81 com o cloud ID conhecido do LogImarui.

A busca no diretório de plugins retornou o Atlassian atual instalado, mas não
forneceu diagnóstico do transporte Legacy. Não foi inferida perda de permissão
ou desconexão OAuth somente a partir dos timeouts. Não houve troca para outro
Jira, alteração de permissões ou transição de issues.

O prompt exige a leitura integral das 70 issues antes da implementação. Essa leitura,
a matriz de aceite individual das 58 subtarefas e toda a implementação permanecem
pendentes do restabelecimento desse acesso. Auditoria e baseline preservam o ponto
de retomada; não substituem a entrega integral solicitada.
