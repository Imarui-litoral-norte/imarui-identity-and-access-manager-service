# Identity

Leia primeiro [a arquitetura global](../../../../../../../../../docs/architecture/README.md). Este módulo possui Identity,
Tenant, IdentityApplication e Affiliation, seus ciclos de vida, persistência e consistência transacional.
Person, LegalEntity e Service são tipos de Identity; Employee, Customer, Supplier, Partner e External são tipos de vínculo.

| Guia proprietário | Conteúdo |
| --- | --- |
| [Regras e estados](business-rules.md) | Invariantes, normalização, chaves e validade efetiva |
| [Fluxos e concorrência](flows-and-concurrency.md) | Onboarding, lifecycle, exclusão histórica, locks e rollback |
| [Contratos de integração](integration-contracts.md) | Ator atual, revogação, snapshot e fronteira com Authentication |
| [Exclusão e histórico](deletion-and-history.md) | Comportamento por agregado, snapshots e unicidade |
| [Testes por feature](testing.md) | Cenários e suites de domínio, aplicação, persistência e HTTP |

O OpenAPI servido em `/docs` é a fonte dos detalhes HTTP: payloads, discriminadores, validações, exemplos, status,
erros e permissions. Aqui ficam as regras que explicam essas operações. Não há catálogo Markdown paralelo de endpoints.

## Ownership

Identity possui cadastro, estado administrativo, onboarding e relações de contexto. Authentication possui credenciais,
sessões, refresh e emissão/consumo de tokens; Authorization terá ownership do catálogo e concessão de acessos.
O módulo declara suas necessidades por ports e não importa modelos internos de autenticação.
Controllers dependem de casos de uso; o domínio não conhece HTTP/JPA/Spring Security.

## Decisões vigentes

Identidades aprovadas iniciam PENDING e exigem ativação explícita. Affiliation inicia ACTIVE; sua utilização depende
também de Identity e Tenant ativos. Desabilitar um Tenant muda a disponibilidade contextual, sem desativar fisicamente
as relações. A exclusão própria remove dados operacionais e conserva histórico transacional.
E-mail é obrigatório e compartilhável inclusive dentro do mesmo IdentityKind. Nickname é obrigatório e único **por IdentityKind**. A aprovação permite escolher qualquer uma das cinco especializações de Affiliation
com seus identificadores próprios. Essas decisões são implementadas e exercitadas por feature.
