# Persistência e migrations

Flyway é proprietário do DDL versionado em `src/main/resources/db/migration`. Hibernate usa validate para detectar divergências.

| Migration | Responsabilidade |
| --- | --- |
| V1 | Baseline do modelo persistido: Identity/JOINED, Tenant, IdentityApplication, Affiliation/JOINED e tabelas administrativas existentes de Authentication |
| V2 | Histórico de Identity e outbox de revogação |
| V3 | E-mail por IdentityKind e chaves naturais de solicitações PENDING |
| V4 | Identificadores específicos de Affiliation únicos por Tenant e integridade dos IDs compostos |

Um schema vazio recebe V1–V4 automaticamente. Um schema populado anterior ao Flyway exige backup e comparação com V1
antes de baseline explícito na versão 1. Reconcilie todas as colunas, tipos e constraints, incluindo e-mail de solicitação
com varchar(254). Não ative baseline automático para contornar inconsistências.
V3/V4 falham quando há conflitos existentes; a adoção deve resolver esses dados por decisão explícita, sem deduplicação
ou exclusão silenciosa. Não edite checksums de migrations já adotadas para acomodar um ambiente divergente.

O runner de testes cria PostgreSQL descartável, aplica migrations do zero e valida o mapeamento JPA. A suíte de persistência
provoca falhas reais em histórico, hard delete e revogação para verificar atomicidade. Consultas, locks e constraints
específicos de Identity são descritos no [guia do módulo](../../src/main/java/br/com/imarui/ima/identity/docs/flows-and-concurrency.md).
