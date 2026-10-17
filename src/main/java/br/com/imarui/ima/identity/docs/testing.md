# Testes de Identity por feature

Execute conforme [o guia global de testes](../../../../../../../../../docs/testing/README.md). Os arquivos estão em src/test/java/br/com/imarui/ima/identity,
organizados por camada e feature. support compartilha setup/fixtures; os cenários pertencem às suites abaixo.

| Feature | Suites principais | Outcomes protegidos |
| --- | --- | --- |
| Identity / lifecycle | IdentityDomainTest, IdentityLifecycleTest, IdentityLifecycleUseCaseTest, IdentityHttpContractTest | Três tipos, transições inválidas, tempo, persistência e leitura titular/permission |
| IdentityApplication | ApproveIdentityApplicationUseCaseTest, IdentityApplicationIntegrationTest, IdentityApplicationHttpContractTest | Create/approve/reject, ator, duplicidade por tipo, chaves naturais, IDs, escolha de vínculo, leituras e concorrência |
| Tenant | TenantDomainTest, TenantHttpContractTest | Criação, consulta, nome, estados, unicidade, acesso e efeitos HTTP |
| Affiliation | AffiliationDomainTest, AffiliationValidityTest, AffiliationIntegrationTest | Cinco tipos, identificadores por Tenant, timestamps e validade contextual sem cascatas |
| Self-delete / history | SelfDeleteIdentityUseCaseTest, IdentitySelfDeleteIntegrationTest, IdentitySelfDeleteHttpContractTest | Alvo pelo ator, motivo, snapshots, remoção JOINED, preservação de terceiros e rollback |
| Revogação | IdentityRevocationIntegrationTest | Falha da outbox reverte lifecycle; reativação não revive token antigo |
| OpenAPI / Swagger | IdentityOpenApiIntegrationTest, IdentitySwaggerUiIntegrationTest | Cada um dos 17 contratos, permissões, exemplos válidos, schemas, erros, Location e assets locais |
| Fluxo composto | IdentityEndToEndTest | Solicitação → aprovação → ativação → exclusão por HTTP |

Persistência usa PostgreSQL real e migrations do zero. Falhas de history/delete/revogação são provocadas com triggers
temporários no banco de teste para provar rollback; não são simuladas apenas com retorno de mock.
Testes HTTP usam servidor real com JWT assinado e verificam status, payload, segurança e persistência relevante.
Exemplos do Swagger são desserializados nos DTOs e validados para impedir documentação que aceita payload impossível.
Resultados e vínculo com as tarefas estão no [registro de aceite](../../../../../../../../../docs/identity-acceptance.md).
