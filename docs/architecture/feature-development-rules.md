# Regras globais para features e fixes

## Contratos, ator e acesso

O código determina normalização, campos aceitos, estados e efeitos. Descrever uma regra no Swagger não implementa
enforcement. `@PreAuthorize` e a segurança HTTP aplicam o acesso; `@RequiredPermission` publica a mesma authority no OpenAPI.
Ownership deve permanecer explícito: ler a própria Identity ou portar IDENTITY_READ é diferente de exigir uma permissão
obrigatória de todos os consumidores. O core recebe o ator por `CurrentActor`, sem consultar SecurityContextHolder.
Operações protegidas de Identity e Authentication também exigem autorização no caso de uso por seus ports
`IdentityOperationAccess` e `AuthenticationOperationAccess`. Após aguardar locks de recurso, revalide a sessão
e a política vigente antes de modificar dados; uma authority obtida no filtro não substitui essa conferência.
O provider em Authorization reutiliza o evaluator central e resolve o Scope do Tenant pelo binding oficial.

## Transações e concorrência

Casos de uso delimitam transações. Locks pessimistas protegem alterações concorrentes de agregados; locks de registro e
constraints protegem chaves naturais. Validação de existência em aplicação não substitui unicidade no banco.
Efeitos duráveis que participam de uma mudança, como histórico e solicitação de revogação, confirmam ou revertem juntos.
Exclusão efetiva grava a entidade de histórico correspondente e remove a linha operacional na mesma transação. Não crie status DELETED, flag deleted ou unicidade que inclua data/estado de exclusão para reter uma entidade removida na tabela principal. Históricos preservam IDs como valores de auditoria, sem FKs operacionais ou reserva de chaves naturais. Suspender/rejeitar não equivale a excluir.
DDL é versionado por Flyway; Hibernate valida. A política operacional fica no [guia de banco](../database/README.md).

## Tempo e erros

Use o Clock global injetado; não crie relógios locais em regras de aplicação. Instantes são UTC com precisão de microssegundos.
Regras puras recebem Instant quando necessário. Preserve invariantes de criação, atualização e primeira ativação.
Erros HTTP seguem RFC 9457, media type application/problem+json, code e timestamp. Handlers traduzem semântica de negócio;
filtros de segurança mantêm o mesmo formato. SQL, stack traces e valores pessoais não entram no contrato de erro.

## OpenAPI e documentação

Toda operação exposta descreve intenção, autenticação/permission/ownership, efeitos observáveis, parâmetros, request,
resposta de sucesso e erros possíveis. Use exemplos fictícios válidos para o DTO e a regra real. Evite anunciar erros
impossíveis para uma superfície pública, exigir campos condicionais globalmente ou apresentar campo de response como editável.
As variantes são declaradas por discriminador/oneOf. A navegação por feature e os metadados documentais são detalhados em
[Platform OpenAPI](../../src/main/java/br/com/imarui/ima/platform/openapi/docs/README.md).

## Testes e conclusão

Organize cenários por feature e camada. Testes de domínio não precisam de Spring; aplicação usa ports mockados quando
adequado; persistência verifica constraints, locks e rollback no PostgreSQL; HTTP exercita comportamento e segurança;
contrato confere o OpenAPI gerado. Confira a [estratégia executável](../testing/README.md).
Reorganização preserva todos os cenários existentes. Cobertura nova deve proteger outcomes relevantes, não apenas repetir
as linhas da implementação. Depois da última mudança funcional ou de teste, valide a árvore final, atualize a evidência
e confira links, escopo de arquivos e ausência de placeholders.
