# Exclusão e histórico de auditoria

## Comportamento por agregado

| Agregado | Estado administrativo | Exclusão efetivamente disponível |
| --- | --- | --- |
| Identity (Person, LegalEntity, Service) | PENDING, ACTIVE, DISABLED | Self-delete do titular ACTIVE: IdentityHistory + remoção física de base/especialização |
| Affiliation (Employee, Customer, Supplier, Partner, External) | ACTIVE, INACTIVE | Removida com a Identity: um AffiliationHistory para cada vínculo e remoção de base/especialização |
| Tenant | PENDING, ACTIVE, DISABLED | Não possui operação de exclusão; disable preserva cadastro/código para reativação |
| IdentityApplication | PENDING, APPROVED, REJECTED | Não possui operação de exclusão; a decisão auditada e os IDs resultantes permanecem |

Nenhum desses enums possui DELETED. Disabled/inactive são suspensões recuperáveis; rejected é uma decisão de onboarding.
Histórico representa a entidade que saiu da operação. Não é um recurso ativo nem um estado adicional de seu enum.
Novas operações de exclusão devem seguir o mesmo contrato antes de serem expostas.

## Históricos JPA independentes

- `IdentityHistoryEntity` → `ima_identities__history`: UUID histórico próprio, UUID original, tipo, estado anterior,
  timestamps de lifecycle, ator, motivo, instante de exclusão e snapshot JSON dos campos comuns/específicos de Identity.
- `AffiliationHistoryEntity` → `ima_affiliations__history`: UUID histórico próprio, UUID original, Identity/Tenant
  originais, tipo, estado anterior, startedAt/updatedAt/endedAt, versão persistida, registration/customerCode/supplierCode
  quando aplicáveis e ator/motivo/deletedAt. `identityHistoryId` correlaciona os vínculos com a exclusão de Identity.

São entidades JPA imutáveis (`@Immutable`), sem setters ou referências JPA aos registros operacionais. Os IDs de ator,
Tenant e Identity são valores históricos; apagar um registro operacional não remove nem altera esses snapshots.
O estado anterior é preservado, inclusive INACTIVE. Não se inventa DELETED para o histórico.

Os históricos não têm unicidade de e-mail, CPF/CNPJ, registration ou códigos. Os índices de pesquisa são não únicos;
a única chave única é o UUID técnico do registro histórico. Aprovar uma nova Identity com os mesmos atributos não
altera o histórico anterior. Os índices operacionais `(kind, email)` e `(tenant, código)` continuam expressando o escopo
real de unicidade do negócio, sem datas ou flags de exclusão. As FKs compostas entre base/especialização de Affiliation
protegem a coerência do Tenant e não são uma solução de soft delete.

## Atomicidade e investigação

O caso de uso bloqueia Identity; o adapter bloqueia seus vínculos em ordem estável, salva os dois históricos e só
então remove as linhas operacionais. A revogação participa da mesma transação. Falha de histórico de Identity,
histórico de vínculo, remoção de especialização, remoção de Identity ou outbox reverte todo o conjunto.
Não há sucesso com remoção sem histórico, nem histórico confirmado de uma remoção que falhou.

Para investigação autorizada, consulte o histórico por UUID original e deletedAt e correlacione IdentityHistory.id
com AffiliationHistory.identityHistoryId. Ator, motivo e instante permitem contextualizar a ação; os IDs de aplicações
aprovadas permanecem informativos. Não há endpoint público de leitura/alteração/exclusão de histórico. O acesso é
restrito à operação administrativa autorizada; snapshots não devem ser enviados a logs ou erros HTTP.

V5 acrescenta o histórico de vínculos sem editar V1–V4. Vínculos já removidos antes desta correção não podem ser
reconstituídos a partir do antigo snapshot de Identity; uma recuperação passada depende de evidência externa/backup.
O novo fluxo protege as exclusões executadas depois da adoção de V5.

## Referência e evidência

O padrão foi conferido no Jeep Club, branch develop:
[ToolRepositoryAdapter](https://github.com/Luskahz/jeep-club-backend/blob/develop/src/main/java/com/jeepclub/backend/tools/infra/persistence/adapter/ToolRepositoryAdapter.java)
bloqueia, salva ToolHistory e remove ToolEntity;
[ToolHistoryEntity](https://github.com/Luskahz/jeep-club-backend/blob/develop/src/main/java/com/jeepclub/backend/tools/infra/persistence/entity/ToolHistoryEntity.java)
preserva os dados e metadados de exclusão em tabela separada. O IAM aplica esse princípio ao seu modelo, com UUID
próprio por snapshot e sem restrição única nos atributos de negócio do histórico.

AffiliationHistoryIntegrationTest verifica recadastro/aprovação/exclusão repetidos com o mesmo e-mail, CPF/CNPJ e código
para três tipos de Identity × cinco vínculos; estado INACTIVE; múltiplos Tenants; preservação de terceiros; chaves
históricas independentes; e rollback real com triggers. IdentitySelfDeleteIntegrationTest mantém a cobertura do
snapshot de Identity e das falhas de seu histórico/remoção. Consulte o [aceite executado](../../../../../../../../../docs/identity-acceptance.md).
