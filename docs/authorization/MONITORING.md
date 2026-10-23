# Sinais e alertas operacionais

Especificação independente de fornecedor. Não foi identificada stack de coleta/alertas no projeto.
Nenhum coletor, dashboard, canal de notificação ou alerta externo foi instalado nesta rodada.
Contexto: IIAMS-128/133/143 e [IIAMS-151](https://logimarui.atlassian.net/browse/IIAMS-151).

## Coleta protegida

O padrão continua expondo somente health. Em ambiente autorizado, habilitar explicitamente
`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`. `/actuator/metrics` e
`/actuator/metrics/{name}` exigem JWT humano com `AUTHORIZATION_AUDIT_READ` efetivo em PLATFORM,
sessão válida e Identity ativa. Administração de Tenant não concede esse acesso global.
O teste HTTP verifica 401 anônimo, 403 administrador local, 200 ROOT e sessão revogada/inativa.
M2M de PDP não é credencial de coleta. Não distribuir senha ROOT para um scraper automático.
Autenticação de coleta automatizada, exporter e rede de gerenciamento exigem decisão operacional
e configuração própria revisada; o serviço não abre um endpoint público para contornar isso.

O registry Micrometer já está presente. O endpoint JSON exporta contagem/tempo total/máximo;
p95/p99 de produção precisam de histogramas ou agregação de amostras no coletor escolhido.
Não calcular percentil a partir de médias, nem somar percentis entre instâncias. Os percentis
dos benchmarks são amostras locais, sem alteração automática da configuração de produção.

## Catálogo verificável

| Sinal Micrometer | Tipo / labels finitas | Significado |
| --- | --- | --- |
| `iiam.pdp.decisions` | counter, `reason` = enum do evaluator | Decisões confirmadas após commit; ALLOWED versus razões de DENY |
| `iiam.pdp.duration` | timer, `outcome=ALLOW/DENY/ERROR` | Avaliação/resolução/auditoria por item; exclui preparação e commit; falha de auditoria é ERROR |
| `iiam.m2m.request` | timer, `outcome` = status HTTP | Filtro completo, incluindo autenticação, controller e commit; inclui catálogo e batch |
| `http.server.requests` | timer do framework, rota parametrizada/status | Latência HTTP por endpoint PDP/batch; escolher somente rotas fixas do serviço |
| `iiam.m2m.proofs` | counter, `outcome=VERIFIED/INVALID_PROOF/REPLAY/ERROR` | Tentativas de validação; VERIFIED não significa operação autorizada/commitada |
| `iiam.authorization.http.failures` | counter, `code` fixo | Erros tratados: indisponibilidade, rejeição, rate limit e falha de auditoria |
| `iiam.authorization.dependency.failures` | counter, `reason=TIMEOUT/DATABASE_OR_TRANSACTION` | Exceções de banco/transação tratadas no controller |
| `iiam.security.audit.failures` | counter, `journal=DECISION/SECURITY/ADMINISTRATIVE` | Falha de escrita crítica; independente de conseguir gravar outra linha no banco |
| `iiam.authorization.policy.snapshot` | timer, sem labels | Custo de carregar a revisão completa, incluindo tentativas com erro |
| `iiam.authorization.policy.rows` | gauge, sem labels | Quantidade da última revisão carregada; não é contador cumulativo |
| `iiam.authorization.mutation.lock` | timer, `outcome=ACQUIRED/ERROR` | Espera pelo advisory lock, inclusive timeout; não mede duração total da mutação |
| `iiam.authorization.mutation.attempts` | counter, `action` interno | Tentativa de auditoria, pode sofrer rollback |
| `iiam.authorization.mutations` | counter, `action` interno | Somente afterCommit; `TECHNICAL_KEY_REVOKED` sinaliza revogação de credencial |
| `iiam.root.transfers` | counter, `event=REQUESTED/CONFIRMED` | Transferências confirmadas por commit; repetição idempotente não incrementa |
| `hikaricp.connections.pending` | gauge do pool, nome estável do pool | Espera por conexão; correlacionar com lock, latência e saturação |

`iiam.authorization.http.failures` cobre os caminhos tratados de Authorization; não inclui
todo erro do servidor/Identity/Authentication. Completar a visão com status HTTP do framework
e health. Batch misto retorna 200: calcular DENY por `iiam.pdp.decisions`, nunca só por HTTP 403.
Contadores de tentativa/erro são locais e não transacionais; decisões e mutações confirmadas
só incrementam após commit. Métricas não substituem o journal persistido e podem se perder em restart.

## Regras para integrar ao coletor escolhido

Valores abaixo são critérios iniciais de configuração, sujeitos a aprovação e baseline do ambiente;
não são SLO aprovado, capacidade garantida ou alertas já ativos. Agregar deltas por instância e
tratar restart/reset de contadores. Ausência de série/coleta é UNKNOWN, nunca saúde presumida.

| Regra | Critério inicial | Severidade / ação |
| --- | --- | --- |
| Falha de auditoria | delta `iiam.security.audit.failures` > 0 em 1 min | Crítica; impedir liberação operacional, investigar escrita/storage/pool, preservar logs |
| Transferência ROOT | cada incremento REQUESTED ou CONFIRMED | Alta; reconciliar aprovação/reason/correlation no journal; notificar responsáveis |
| Replay | qualquer incremento REPLAY; escalar se repetido por 5 min | Alta; investigar reuso de prova/retry incorreto; nunca relaxar nonce |
| Indisponibilidade | HTTP 5xx em PDP > 1% por 5 min, mínimo 100 chamadas | Alta; correlacionar banco/timeout/auditoria; PEP continua fail-closed |
| Falha absoluta de integração | probe autorizado falha 3 vezes ou coleta ausente por 2 intervalos | Alta; distinguir ausência de tráfego de indisponibilidade |
| Latência/timeout | p95 acima do orçamento aprovado do PEP por 5 min ou crescimento de TIMEOUT | Alta; levantar distribuição completa e tempo de pool/lock |
| Rejeição técnica | INVALID_PROOF ou 401 > baseline por 5 min | Média; verificar rotação, audience/issuer, relógio, proxy e encoding; não registrar prova |
| Saturação | RATE_LIMITED recorrente ou pool pending persistente por 5 min | Média/Alta conforme impacto; medir origem e capacidade antes de elevar limites |
| Contenção | crescimento sustentado de timer de lock e espera no pool | Média; identificar mutações longas; não remover serialização |
| Mudança de negações | proporção de DENYs aumenta frente ao baseline com mínimo 100 decisões | Média; distinguir política legítima de incidente |
| Credencial revogada | incremento de mutation `TECHNICAL_KEY_REVOKED` | Informativa/Alta se inesperada; reconciliar operador e chave pelo journal |

Sem denominador suficiente, usar eventos absolutos e investigação; não produzir porcentagens
enganosas. Root e falha de auditoria têm detecção por evento, sem depender do piso de tráfego.
Monitorar espaço/WAL/replicação e locks reais no PostgreSQL através da infraestrutura escolhida.

## Logs, privacidade e entrega de alertas

Sinais em formato de evento com campos explícitos: `authorization_decision` (decisionId,
correlationId, outcome, reason), `authorization_root_transfer` (event, transferId, scopeId,
correlationId), `security_audit_failure` (journal, correlationId). Correlacionar campos exatos;
não extrair UUIDs como labels de métricas. Adaptar parsing ao formato do appender no coletor.
Não habilitar logging de corpos, headers Authorization, senhas ou diagnósticos SQL com valores.
Os novos logs de falha não incluem texto da exceção/SQL. `REPLAY_DETECTED` é persistido sem
confiar no ator/serviço/correlação da prova rejeitada; a correlação de rejeição é gerada pelo servidor.

Operação deve definir coletor, credenciais/rede, destino autorizado, escalonamento, deduplicação,
armazenamento e teste de notificação. Validar inicialmente em homologação: replay sintético,
transferência de teste, falha controlada de auditoria e interrupção de coleta. Os testes do app
validam sinais locais e rollback; não demonstram entrega de e-mail/webhook/pager externo.
