# Regras de negócio e estados

## Identity e tipos

Campos comuns: UUID estável, e-mail primário, status e timestamps de lifecycle. Person acrescenta nome, CPF,
nascimento e referências de telefone/foto; LegalEntity acrescenta razão social, nome fantasia e CNPJ;
Service acrescenta nome e descrição. O snapshot histórico conserva as diferenças e metadados persistidos desses tipos.

| Estado atual | Operação válida | Resultado |
| --- | --- | --- |
| PENDING | activate | ACTIVE com primeira activatedAt |
| ACTIVE | disable | DISABLED com disabledAt |
| DISABLED | reactivate | ACTIVE, limpa disabledAt e preserva activatedAt |

Transições repetidas ou incompatíveis são conflitos de domínio. updatedAt não retrocede. createdAt permanece estável.
Não existe estado DELETED. Exclusão é um fluxo destrutivo separado, iniciado pela própria Identity autenticada ACTIVE.

## Identificadores e normalização

E-mail é aparado e convertido para minúsculas. Sua unicidade considera o tipo, tanto nas Identities operacionais
(em qualquer estado) como nas aplicações PENDING. O mesmo e-mail pode pertencer a tipos diferentes.
CPF/CNPJ são normalizados para dígitos e validados por seus value objects; suas chaves operacionais são únicas.
Solicitação PENDING duplicada por CPF/CNPJ é bloqueada. Rejeição libera a reserva pendente, mas não altera cadastro existente.
Histórico não reserva e-mail, CPF, CNPJ ou códigos de vínculo, permitindo novo cadastro/aprovação após self-delete, mesmo com os mesmos valores. Cada vínculo removido também possui seu próprio registro AffiliationHistory. Veja a [política de exclusão e auditoria](deletion-and-history.md).
Limites e formatos do payload bruto e a forma canônica de resposta são publicados no OpenAPI dos DTOs.

## Tenant

Usa PENDING → ACTIVE ↔ DISABLED. code é estável, normalizado para maiúsculas e único; name é aparado e editável
somente em PENDING/ACTIVE. A primeira ativação é preservada na reativação. Somente ACTIVE pode provisionar novas identidades.
Não existe DELETED, delete de Tenant ou TenantHistory neste escopo.

## Affiliation

| Tipo | Identificador específico obrigatório |
| --- | --- |
| EMPLOYEE | registration |
| CUSTOMER | customerCode |
| SUPPLIER | supplierCode |
| PARTNER | Nenhum |
| EXTERNAL | Nenhum |

Campos de um tipo não podem ser enviados para outro na seleção de aprovação. Identificadores são aparados,
não vazios e limitados a 255 caracteres; são únicos no Tenant, incluindo relações inativas.
Affiliation ACTIVE pode ser desativada para INACTIVE, preenchendo endedAt. Reativação limpa endedAt e preserva startedAt.
Eventos anteriores a updatedAt e estados reconstruídos inconsistentes são rejeitados. INACTIVE é suspensão recuperável e mantém a reserva do código; a exclusão física por self-delete arquiva e libera essa reserva.

## Validade contextual

`AffiliationValidity.isEffective` exige IDs correspondentes e Identity ACTIVE + Affiliation ACTIVE + Tenant ACTIVE.
Consultas operacionais e existência ativa usam essa política. Leituras administrativas por ID incluem registros inativos
para permitir lifecycle. Desabilitar Tenant não muda IdentityStatus/AffiliationStatus; reativar não revive vínculo INACTIVE.
