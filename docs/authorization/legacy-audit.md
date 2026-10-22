# IIAMS-81 — auditoria inicial do Authorization

Data: 2026-10-22. Base inspecionada: `f419a501328774fefec24b6ac28070e19f972cf8`
(`main`, após integração do PR #2). Branch de trabalho: `feat/iiams-81-authorization-complete`.

## Estado e condição para implementação

Esta é a auditoria do código existente, anterior à implementação. Não representa entrega
das Stories, validação de critérios de aceite ou substituição dos requisitos do Jira.
O prompt anexado exige leitura integral de IIAMS-81, IIAMS-82–92 e IIAMS-93–150 antes
de escrever código. As consultas realizadas pelo Atlassian Rovo Legacy expiraram;
as descrições e comentários das 70 issues ainda não foram obtidos.

O GitHub confirma `main` como branch padrão, o PR #2 integrado e a base acima.
A árvore da antiga branch de Authentication (`1603b22`) é idêntica à árvore dessa base.
O checkout é o repositório `imarui-identity-and-access-manager-service`, independente
do diretório `imarui-authentication-service` usado como contexto inicial do chat.
O remote local ainda usa o nome antigo; a base foi obtida pela URL canônica do IIAM.

## Fundação existente

O módulo contém quatro modelos de domínio, catálogo interno por enums, um port de
consulta, adapters JDBC, bootstrap transacional e provider de authorities. Não possui
controllers administrativos, AuthorizationScope, DENY, delegação, catálogo externo,
PDP contextual ou autenticação técnica específica para consumidores do PDP.

`V9__authorization_foundation.sql` cria:

| Tabela | Fundação e limitações observadas |
|---|---|
| `authorization_permissions` | ID bigint preservável; código único; descrição e módulo; sem proprietário de serviço, namespace externo, versão ou descontinuação |
| `authorization_roles` | ID bigint preservável; nome globalmente único; active; sem ownership GLOBAL/SCOPE/TENANT |
| `authorization_role_permissions` | Associação por IDs, unicidade e referências; sem semântica de delegação |
| `authorization_identity_roles` | UUID de Identity, Role, timestamp; chave Identity/Role; scope textual obrigatório e limitado a PLATFORM |

V1–V10 já existem. Nenhuma dessas migrations deve ser reescrita. A próxima migration
precisa preservar IDs, relações e históricos, além de testar upgrade populado de V10.
O modelo novo não cabe na restrição `scope='PLATFORM'` ou na chave Identity/Role atual.

## Classificação dos componentes

As classificações abaixo indicam o tratamento necessário observado no código;
a solução final depende da reconciliação com as descrições formais do Jira.

| Componente | Classificação | Evidência e tratamento necessário |
|---|---|---|
| `core/domain/model/RolePermission` | REUSE | Associação pura com validação de IDs e timestamps; manter identidade e integridade, aplicar delegação no caso de uso |
| Exceptions de descrição de Permission | REUSE | Validações locais sem dependência de infraestrutura |
| `PermissionCodeMismatchException` | REFACTOR | Regra de identidade do código aproveitável; assinatura restrita ao enum interno precisa acompanhar a evolução do catálogo |
| `api/catalog/PermissionCode` e `ModuleCode` | REFACTOR | Podem continuar representando capacidades internas; não servem como limite fechado para o catálogo federado |
| `api/catalog/PermissionDefinition` | REFACTOR | Deriva descrição e módulo do nome; qualquer novo código que não comece com AUTHENTICATION recebe IDENTITY, inclusive futuros códigos de Authorization |
| `core/domain/model/Permission` | REFACTOR | Regras de descrição e identidade são úteis; código/módulo enum impedem Permissions de serviços externos |
| `core/domain/model/Role` | REFACTOR | Preservar IDs; acrescentar ownership, disponibilidade e ciclo de vida conforme requisitos |
| `core/domain/model/IdentityRole` | REPLACE | O próprio comentário o limita ao bootstrap PLATFORM; evoluir para RoleAssignment contextual, sem perder grants existentes |
| `core/repository/AuthorizationCatalog` | REFACTOR | Port somente de leitura da fundação; falta persistência e consulta da política contextual |
| `infra/persistence/AuthorizationCatalogAdapter` | REFACTOR | SQL parametrizado aproveitável; `PermissionCode.valueOf` e `ModuleCode.valueOf` impedem catálogo externo; updatedAt é preenchido com createdAt |
| `infra/security/AuthorizationIdentityAuthoritiesProvider` | REPLACE | Consulta grants PLATFORM e Identity ativa, sem recurso, hierarquia, Tenant, DENY ou delegação; não pode representar o algoritmo final |
| `api/module/IdentityAuthorities` | REFACTOR | Integração pública existente sem contexto de recurso; preservar compatibilidade deliberada durante a adoção do PDP |
| `infra/bootstrap/PermissionCatalogBootstrap` | REFACTOR | Lock transacional e upsert são aproveitáveis; hoje concede todas as Permissions persistidas ao ROOT em cada inicialização |
| `platform/bootstrap/AuthenticationBootstrap` | REFACTOR | Preservar execução única, invitation e arquivo protegido; escrita direta em IdentityRole deve integrar a atribuição nova sem permitir novos ROOT arbitrários |
| `platform/security/IdentityApiSecurityConfiguration` | REFACTOR | Validação atual de JWT, sessão, Identity e cutoff é aproveitável; authorities são recarregadas do banco, mas sem contexto de recurso |
| V1–V10 | REUSE | Histórico publicado; alterações exclusivamente por migrations incrementais |
| Legado a remover | REMOVE pendente | Nenhuma exclusão segura foi determinada antes da leitura do Jira; não remover contratos apenas por origem no Jeep Club |

## Integrações e invariantes a preservar

Authentication valida RS256, issuer/audience, tempos, UUID do subject, SID, proprietário,
validade da sessão, Identity e cutoff. O converter ignora authorities declaradas no JWT e
consulta `IdentityAuthorities` em cada requisição. Essa atualização por requisição é útil,
mas não implementa autorização pelo Scope do recurso.

Identity usa contratos de ator, revogação e exclusão com históricos. A associação da V9
tem FK com `on delete cascade` para Identity. O novo Authorization deverá preservar
auditoria independente das linhas operacionais e impedir exclusão/desativação do último
administrador de recuperação antes do commit, em coordenação com os fluxos Identity.

O bootstrap do catálogo usa `pg_advisory_xact_lock(390047)` e o bootstrap inicial usa
`pg_advisory_xact_lock(390076)`. Locks existentes não demonstram proteção de todos os
futuros caminhos de alteração da política ou prevenção de lockout concorrente.

Nenhum vínculo Affiliation é consultado pelo provider atual. A nova implementação deve
continuar distinguindo autorização de requisitos próprios de negócio relativos a vínculo.

## Riscos e decisões ainda necessárias

1. Definir a conversão de PLATFORM sem ampliar grants a raízes independentes ou perder acesso existente.
2. Substituir unicidade global do nome de Role por unicidade de proprietário, preservando IDs.
3. Separar registro de catálogo de concessão: o cross join atual do ROOT concederia automaticamente Permissions externas.
4. Preservar recuperação por prevenção de estados inválidos, sem bypass de DENY no PDP.
5. Determinar Scope a partir do recurso e autenticar o serviço técnico; um UUID recebido do frontend não constitui prova de ownership.
6. Serializar mutações concorrentes relevantes, validar ciclos e testar rollback e reparenting.
7. Integrar enforcement contextual às APIs existentes sem transformar uma authority plana em autorização de todos os Tenants.
8. Não usar o estado de login ou claims antigos como substituto da política atual.

Estas são constatações técnicas e pontos de reconciliação, não contradições de negócio
confirmadas. Nenhuma interpretação nova de negócio foi adotada antes da leitura formal.

## Sequência de migração e validação

Após recuperar o acesso ao Jira Legacy: registrar as 70 descrições e critérios; produzir
matriz individual das 58 subtarefas; reconciliar cada risco acima; implementar fundação,
política, administração e integrações em commits incrementais na mesma branch.

A evolução deverá usar PostgreSQL real: instalação limpa; upgrade de V10 populado com
Role/Permission/IdentityRole; preservação de IDs e dados Identity/Authentication;
isolamento entre raízes/irmãos; DENY absoluto; delegação; ownership; M2M e sessões;
prevenção de lockout; concorrência e rollback. A regressão inicial é evidência da base,
não evidência de implementação do Epic.

Gateway IIAMS-80, produção, merge e deploy permanecem fora desta execução.

Evidência da validação inicial e limitações do ambiente: [baseline-validation.md](baseline-validation.md).
