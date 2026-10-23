# Auditoria: capacidade, consulta e retenção

## Evidência e índices

[Ensaio isolado](round-4-audit-capacity.json): 20 mil linhas por journal, 100 Scopes,
100 consultas da união paginada com count, Scope restrito e página de 25 registros.
O teste verifica isolamento da página, filtro de correlação, planos EXPLAIN ANALYZE/BUFFERS
e bloqueio de UPDATE/DELETE. Dados curtos sintéticos não representam o tamanho de before/after
de Roles compartilhadas ou manifests reais. Não foi excluído histórico para obter desempenho.

V11/V20/V22/V24 já cobrem:

| Consulta | Índices existentes |
| --- | --- |
| Scope + período | `ix_authorization_audit_scope`, `ix_security_event_scope_time` |
| Correlation ID | `ix_authorization_audit_correlation`, `ix_security_event_correlation` |
| Período/ordem global | `ix_authorization_audit_time`, `ix_security_event_time` |
| Recurso | `ix_authorization_audit_resource`, `ix_security_events_resource` |
| Históricos excluídos | índices por ID original/instante de exclusão em Roles e Scopes |

Os planos medidos utilizaram os índices existentes. Sem evidência de gargalo que justifique
novos índices, não foi criada V26. Índices extras aumentariam custo de escrita, WAL e espaço.
Não confundir `Flyway validated 26 migrations` em schema descartável com V26: inclui a linha
de criação de schema; versões SQL continuam V1–V25.

A API atual oferece Scope, evento e correlação, paginação limitada a 100. Inclui count total
e OFFSET; páginas profundas e Scopes amplos continuam custosos. Não foi alterado o contrato.
Investigação por período/recurso pode usar consulta SQL parametrizada de operador autorizado
somente leitura, restringindo jurisdição e minimizando exportação. Exemplo para journal técnico:

```sql
SELECT id,event,occurred_at,reason_code,correlation_id
FROM authorization_security_events
WHERE scope_id = $1 AND occurred_at >= $2 AND occurred_at < $3
ORDER BY occurred_at DESC,id LIMIT 100;
```

O SQL não substitui enforcement da API: autorização/Scope devem ser resolvidos antes de acesso
operacional e exportação. Exports auditáveis não podem vazar histórico de outras empresas.

## Dimensionamento

Cada PDP unitário bem autenticado normalmente grava um evento M2M e um PDP. Para taxa `d`
decisões unitárias/s, estimativa inicial `2*d*86400` linhas/dia, além de rejeições e administração.
Batch de 20 grava um M2M e até 20 decisões, alterando a razão. Nenhum evento de decisão é
amostrado/suprimido para reduzir armazenamento.

Exemplos de carga, não previsão: 5/50/500 decisões unitárias/s representam aproximadamente
864 mil / 8,64 milhões / 86,4 milhões de linhas diárias. Multiplicar por bytes físicos por linha
medidos no ensaio e por dias do cenário fornece uma ordem de grandeza, não prazo de retenção.
Medir novamente com payloads reais autorizados, bloat, índices, WAL, réplicas, backups e margem.
O JSON registra `pg_total_relation_size` e tamanho lógico médio, diferenciando tabela/índices.
Monitorar crescimento diário, espaço livre, duração de consulta/count, autovacuum e WAL.

## Política pendente e opções

Nenhum prazo legal/operacional foi aprovado. Não há TTL destrutivo, job de DELETE/TRUNCATE,
desabilitação de trigger ou alteração de imutabilidade nesta entrega.

| Opção a aprovar | Benefício | Risco / condição |
| --- | --- | --- |
| Manter online com capacidade ampliada | Busca e integridade atuais preservadas | Crescimento de custo; requer orçamento e monitoração |
| Exportar cópia criptografada e imutável | Evidência externa e recuperação independente | Não reduz banco por si; conferir contagem/hash/manifesto/restauração, acesso e custódia |
| Particionar no futuro por período | Operação por faixas e potencial gestão de índices | Migration dedicada, PK/uniqueness/triggers/FKs, consultas e histórico precisam de review e ensaio |
| Arquivamento com remoção aprovada | Reduz conjunto quente quando legalmente permitido | Exige retenção formal, legal hold, duas aprovações, integridade verificável, busca histórica e plano de recuperação |

Decisões necessárias: proprietário dos dados, classes de evento/histórico, jurisdição/obrigações,
prazo por classe, legal hold, RPO/RTO, custódia/chaves, acesso e critérios de prova/expurgo.
Append-only não impede superusuário de adulterar o banco: restringir privilégios/DDL, acesso
administrativo e manter evidência externa conforme a governança aprovada.
Backup não substitui arquivo pesquisável e não autoriza remover os originais.
