# Diretório de empresas para associação de identidades

`GET /api/v1/tenants` fornece os nomes/códigos de empresas já existentes para seleção humana no frontend. O gateway usa `/api/iam/api/v1/tenants`.

Parâmetros: `q` (nome ou código, até 150 caracteres), `status` (`ACTIVE`, padrão; `PENDING`, `DISABLED`, `ALL`), `page` (0–100000), `size` (1–100, padrão 20). Retorno `{items: TenantResponse[], total, page, size}`, ordenado por nome sem diferenciar maiúsculas, depois UUID. A busca é literal: caracteres `%`, `_` e `\` não ampliam a pesquisa como curingas SQL.

O endpoint exige uma sessão autenticada e verifica TENANT_READ pela política atual, usando as vinculações autoritativas de tenant/escopo. Leitura restrita a um contexto funciona mesmo sem TENANT_READ na plataforma. As empresas não autorizadas, inclusive por negação explícita, são excluídas **antes** de aplicar busca, contagem ou paginação. Sem autoridade de leitura em qualquer contexto, retorna 403. Sem sessão, 401. Parâmetros inválidos, 400. Não cria empresas, vínculos, perfis ou concessões de acesso.

O frontend usa ACTIVE ao aprovar uma solicitação e ao selecionar o proprietário de um perfil. A área Empresas também permite consultar pendentes/desabilitadas para seu ciclo de vida. O UUID continua sendo a chave do payload; o operador escolhe pelo nome/código, sem copiá-lo. Uma única empresa ativa visível é pré-selecionada; uma escolha anterior é mantida durante a navegação na área. A aprovação revalida no serviço o estado atual da empresa, independentemente do diretório.

Não há alteração de banco/migração. Ativação da funcionalidade exige a versão atualizada do IIAM **e** do frontend. Atualizar somente o frontend não cria o endpoint no processo IIAM em execução.

Validação: `TenantDirectoryHttpContractTest`, contratos de tenant existentes, OpenAPI e administração de autorização — 39 testes passaram, com PostgreSQL isolado descartável. Inclui busca por nome/código, paginação, estados, curingas literais, leitura restrita por escopo, ausência de vazamento em totais/resultados, negação explícita e remoção da autoridade com JWT ainda existente.
