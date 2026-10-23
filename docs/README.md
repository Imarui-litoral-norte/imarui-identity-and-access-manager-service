# Documentação do Imarui IAM

Esta é a entrada documental do serviço. O código integrado e os testes executáveis determinam o comportamento vigente.
O contrato HTTP é publicado no OpenAPI; os guias explicam arquitetura, domínio e operação.

| Nível | Fonte | Conteúdo proprietário |
| --- | --- | --- |
| Global | [Arquitetura](architecture/README.md) | Ownership, camadas, integração e regras transversais |
| Módulo | [documentação do módulo Identity](../src/main/java/br/com/imarui/ima/identity/docs/README.md) | Modelos, invariantes, fluxos e concorrência de Identity |
| Módulo | [Authorization](authorization/ARCHITECTURE.md) e [revisão corretiva](authorization/ROUND_3_REVIEW.md) | RBAC hierárquico, PDP/M2M, administração, recuperação, auditoria, rastreabilidade e parecer atual |
| HTTP | `/docs` e `/v3/api-docs` | Endpoints, parâmetros, variantes, exemplos, acesso e erros |
| Operação | [Ambiente local](operations/local-development.md) e [banco](database/README.md) | Configuração, inicialização e migrations |
| Validação | [Estratégia de testes](testing/README.md) | Comandos, isolamento e cobertura por feature |
| Evidências | [Aceite de Identity](identity-acceptance.md) | Rastreabilidade das tarefas e resultados executados |

Antes de alterar uma feature, leia a arquitetura global e o módulo proprietário, confira o código e o OpenAPI
afetados e atualize essas fontes na mesma mudança. Preserve o ownership de cada informação para evitar guias divergentes.
