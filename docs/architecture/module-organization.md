# Organização dos módulos e camadas

Leia o [índice global](README.md). Os pacotes existentes têm responsabilidades explícitas:

| Área | Ownership atual |
| --- | --- |
| `identity` | Identity e especializações Person/LegalEntity/Service, Tenant, IdentityApplication e Affiliation |
| `authentication` | Credenciais humanas/técnicas, primeiro acesso, login, sessões, refresh rotativo, reautenticação administrativa e consumo da outbox; ver ../authentication/README.md |
| `platform` | Composição Spring, segurança HTTP, Clock, OpenAPI e tratamento transversal de erros |
| `authorization` | Hierarchical Scoped RBAC: Scopes, catálogo federado, ownership/lifecycle de Roles, assignments, DENY, delegação, PDP, APIs administrativas, recuperação ROOT e auditoria; ver [arquitetura](../authorization/ARCHITECTURE.md), [consumidores](../authorization/CONSUMER_GUIDE.md) e [relatório final](../authorization/ROUND_2_FINAL_REPORT.md) |

```text
identity/
├── api/                 controllers, DTOs, validação, handlers e descrição HTTP
├── core/
│   ├── application/     comandos, resultados, casos de uso, serviços de aplicação e ports
│   ├── domain/          agregados, value objects, enums, políticas e exceptions
│   └── repository/      contratos de persistência do domínio
├── infra/
│   ├── persistence/     entities, JPA repositories, mappers e adapters
│   └── security/        adapter de ator autenticado
└── docs/                conhecimento específico de Identity
```

O padrão vigente usa classes de caso de uso em `core.application.usecase`. Organizar documentação e testes por feature
não exige substituir esse padrão ou copiar a estrutura de services de outro projeto.

Controllers validam e convertem contratos HTTP, delegam ao caso de uso e mapeiam o resultado. Domínio não importa JPA,
HTTP ou Spring Security. A aplicação define transações e usa contratos do próprio módulo. Os adapters traduzem esses
contratos para persistência e infraestrutura. Composição Spring transversal fica em Platform.

Uma integração usa porta com identificadores e semântica mínima. Identity publica `CurrentActor`, `AccessRevocation`,
`IdentityDeletion` e `IdentityRegistrationLock` em sua aplicação; providers de infraestrutura implementam essas necessidades.
Consumidores futuros devem usar uma API pública deliberada ou adapter para um port, preservando os modelos internos.

Relações físicas no banco não concedem ownership a outro módulo. Sessões, credenciais e roles não se tornam campos de
Identity por conveniência. Detalhes da integração vigente estão nos docs internos do módulo.
