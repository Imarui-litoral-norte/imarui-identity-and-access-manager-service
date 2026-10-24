# Identificação global e aprovação de acesso — IIAMS-177

Apelido e e-mail são identificadores globais, normalizados e únicos entre PERSON,
LEGAL_ENTITY e SERVICE. O UUID continua sendo a chave primária e o subject do JWT.
Identidades desabilitadas continuam reservando seus identificadores. Histórico de
exclusão e solicitações rejeitadas não os reservam.

## Autenticação

Login recebe `identifier` e `password`; recuperação e pedido de ativação recebem
`identifier`. Apelido, CPF, CNPJ e e-mail resolvem a mesma Identity. O campo legado
`identityKind` é opcional e não participa da resolução ou do rate limit.
Cookies, CSRF, assinatura, sessões e respostas genéricas de recuperação permanecem
com o contrato existente. E-mail de serviço precisa ser distinto do e-mail de outra
Identity, inclusive o do responsável por essa integração.

## Solicitações públicas

- `POST /api/v1/identity-applications/users`: `name`, `document`, `nickname`,
  `email`; CPF admite `birthDate` e `phoneNumber`, CNPJ admite `tradeName`.
- `POST /api/v1/identity-applications/services`: `name`, `nickname`, `email`,
  `description` opcional. Não exige documento.

Ambos retornam 201, Location e uma solicitação PENDING. Não criam Identity,
credencial, sessão ou permissão. O POST polimórfico anterior foi retirado; consultas
continuam nas mesmas rotas e exigem as permissões administrativas existentes.

## Aprovação administrativa

`POST /api/v1/admin/identity-applications/{id}/approve` recebe `tenantId`,
`affiliation` e `identityKind` final. O tipo final pode ser o indicado pelo documento
ou SERVICE; sem documento, somente SERVICE. Omitir o tipo conserva o tipo solicitado
para compatibilidade com clientes administrativos anteriores.

`type` e `details` da resposta continuam representando a solicitação original;
`approvedKind` registra separadamente o tipo final, junto do reviewer e reviewedAt
já existentes. Solicitações anteriores aprovadas recebem seu tipo original na V27.
Quando o tipo aprovado é SERVICE, o nome original é preservado integralmente; CPF
ou CNPJ permanecem na solicitação e não passam a autenticar a Identity técnica.
Não há conversão de identidades existentes.

## Upgrade e concorrência

Executar `scripts/preflight-global-identifiers.sql` em conexão de leitura. O relatório
mostra apenas IDs dos recursos conflitantes. V26 verifica novamente as colisões e
aborta a transação antes de alterar índices. Resolver explicitamente qualquer
conflito antes de repetir; não usar Flyway repair para contornar dados ambíguos.
V27 adiciona o tipo aprovado, preserva dados anteriores e acomoda nomes de até
150 caracteres ao criar serviço a partir de uma solicitação de usuário.

Cadastro, aprovação e alteração de apelido usam locks transacionais comuns;
solicitações pendentes reservam apelido e e-mail globalmente. A aprovação transfere
o uso para a Identity na mesma transação; rejeição libera a reserva da solicitação.
Os índices únicos continuam protegendo escritas concorrentes no PostgreSQL.
