# Arquitetura e governança documental

O IAM organiza domínio, aplicação, adapters e composição técnica. Este diretório descreve decisões que atravessam
features. As regras de Identity pertencem a [documentação do módulo Identity](../../src/main/java/br/com/imarui/ima/identity/docs/README.md).

## Leitura e manutenção

1. [Organização e ownership](module-organization.md).
2. [Regras para features e fixes](feature-development-rules.md).
3. Documentação interna do módulo que possui o dado ou a regra.
4. Controllers, DTOs, handlers e OpenAPI correspondentes.
5. [Testes](../testing/README.md), [operação](../operations/local-development.md) e [persistência](../database/README.md), quando afetados.

| Conhecimento | Fonte de verdade |
| --- | --- |
| Fronteiras e responsabilidade das camadas | Estes guias globais |
| Estados, invariantes e decisões do domínio | Docs internos do módulo |
| Método/path, payloads, parâmetros, validação HTTP, status e permissões | OpenAPI gerado pelos controllers/DTOs/customizers |
| Procedimento operacional e configuração | Guias de operação e banco |
| Evidência de comportamento | Testes executados no código final |

Docs de módulo referenciam estas regras; não copiam instruções transversais. O OpenAPI contém os detalhes de endpoints
e exemplos; os Markdown de domínio explicam intenção e consistência sem manter um catálogo paralelo de rotas.
Tickets e propostas futuras não constituem comportamento implementado.

A organização documental segue a referência do [Jeep Club](https://github.com/Luskahz/jeep-club-backend/blob/develop/docs/architecture/README.md),
adaptada à arquitetura de casos de uso e aos modelos efetivamente presentes neste serviço.
