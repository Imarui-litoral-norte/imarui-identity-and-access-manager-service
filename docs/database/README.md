# Persistência e migrations

Flyway é proprietário do DDL versionado em `src/main/resources/db/migration`. Hibernate usa validate para detectar divergências.

| Migration | Responsabilidade |
| --- | --- |
| V1 | Baseline do modelo persistido: Identity/JOINED, Tenant, IdentityApplication, Affiliation/JOINED e tabelas administrativas existentes de Authentication |
| V2 | Histórico de Identity e outbox de revogação |
| V3 | E-mail por IdentityKind e chaves naturais de solicitações PENDING |
| V4 | Identificadores específicos de Affiliation únicos por Tenant e integridade dos IDs compostos |
| V5 | Histórico independente dos vínculos removidos junto com Identity, incluindo estado/dados específicos e metadados de exclusão |
| V6–V10 | Nickname, credenciais/challenges, sessões/refresh, fundação de Authorization e estabilização de Authentication |
| V11–V17 | Scopes/binding Tenant, catálogo federado, ownership/assignments, DENY/delegação, recuperação ROOT, histórico de Identity e ownership explícito |
| V18–V24 | Unicidade DENY, lifecycle/histórico de Role, autenticação técnica, transferência ROOT, auditoria imutável, índices e proteção de lifecycle de assignment |

Um schema vazio recebe V1–V24 automaticamente. O [guia operacional de Authorization](../authorization/OPERATIONS.md)
explica upgrades V10/V17/V20, reconciliação auditada e rollback. V1–V17 não foram reescritas pelo Epic IIAMS-81.
Um schema populado anterior ao Flyway exige backup e comparação com V1
antes de baseline explícito na versão 1. Reconcilie todas as colunas, tipos e constraints, incluindo e-mail de solicitação
com varchar(254). Não ative baseline automático para contornar inconsistências.
V3/V4 falham quando há conflitos existentes; a adoção deve resolver esses dados por decisão explícita, sem deduplicação
ou exclusão silenciosa. Não edite checksums de migrations já adotadas para acomodar um ambiente divergente.

O runner de testes cria PostgreSQL descartável, aplica migrations do zero e valida o mapeamento JPA. A suíte de persistência
provoca falhas reais em histórico, hard delete e revogação para verificar atomicidade. Consultas, locks e constraints
específicos de Identity são descritos no [guia do módulo](../../src/main/java/br/com/imarui/ima/identity/docs/flows-and-concurrency.md).
