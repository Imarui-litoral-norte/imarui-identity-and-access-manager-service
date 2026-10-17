# Platform OpenAPI e Swagger personalizado

A composição global está em IdentityOpenApiConfiguration; a UI personalizada abre em /docs e carrega /v3/api-docs.
HTML/CSS/JavaScript ficam em src/main/resources/static/openapi-custom, utilizando os bundles locais do springdoc.

## Metadados e ownership

| Mecanismo | Contrato |
| --- | --- |
| @Operation / DTO @Schema | Intenção, descrição, campos e variantes por endpoint |
| @SwaggerOperationGroup | x-operation-group e x-operation-group-order, com fallback da annotation de classe |
| @RequiredPermission | x-required-permissions e bearerAuth; serve à documentação, não aplica autorização |
| IdentityOperationCustomizer | Acesso titular/permission/público, exemplos válidos, parâmetros UUID, Location e outcomes específicos de Identity |
| ApiProblem | Schema documental do ProblemDetail com code/timestamp; o runtime continua usando o contrato transversal RFC 9457 |

O customizer global publica agrupamento e permission para os handlers anotados. Conteúdo semântico de negócio e exemplos
ficam no módulo proprietário, não em Platform. O campo x-access-mode distingue PUBLIC, PERMISSION,
AUTHENTICATED_OWNER e OWNER_OR_PERMISSION. Neste último caso, x-alternative-permissions não significa permissão obrigatória
do titular. As anotações devem refletir o enforcement real, que permanece nos filtros e @PreAuthorize.

## Navegação

Swagger renderiza uma tag principal por módulo e subgrupos ordenados por feature. Os grupos iniciam fechados;
o script reaplica agrupamento quando operações são renderizadas, sem sobrescrever details.open já escolhido pelo usuário.
Busca, parâmetros, Try it out, Authorize e esquemas continuam sendo os recursos do Swagger UI.
A autorização não persiste no armazenamento do navegador. Todos os assets são locais e não usam CDN.

A implementação foi adaptada da [UI do Jeep Club](https://github.com/Luskahz/jeep-club-backend/tree/develop/src/main/resources/static/openapi-custom).
Alterar o estilo não muda os contratos HTTP ou a semântica de uma permissão.
Os testes de contrato de Identity verificam os 17 endpoints, os exemplos contra DTOs e os recursos públicos da UI;
a revisão em navegador confirma agrupamento e interações reais.
