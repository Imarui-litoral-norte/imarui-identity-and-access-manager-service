# Contratos de integração

## Ator atual

CurrentActor publica somente `requireIdentityId()`. O adapter exige autenticação válida com subject UUID e cadastro ACTIVE.
Ausência, inconsistência ou estado inválido produz erro explícito. Approve/reject e self-delete usam essa porta;
não aceitam reviewer/ator escolhido no payload. Testes de aplicação podem usar fake/mock sem contexto web.

## Revogação e consistência

AccessRevocation recebe identityId ou tenantId. Disable de Identity e self-delete solicitam revogação por Identity;
disable de Tenant solicita revogação contextual. Reactivate não solicita restauração.
O adapter exige transação existente e grava ima_access_revocation_requests com UUID, scope, subjectId,
requestedAt e processedAt opcional. Falha na gravação propaga e reverte o lifecycle/exclusão; não há no-op silencioso.

Authentication deverá consumir os pedidos idempotentemente, invalidar sessões/refresh correspondentes e registrar
processedAt somente depois de concluir seus próprios efeitos. Deve repetir falhas e manter o instante de cutoff;
os pedidos processados também integram o histórico usado para rejeitar tokens anteriores à revogação.
O consumidor de sessões/refresh e a emissão de tokens pertencem à próxima evolução de Authentication.

O resource server já rejeita Identity desabilitada/removida, tenant_id sem vínculo efetivo e token com iat anterior
ou igual ao pedido aplicável. Reativação não torna esse token válido. Como iat usa segundos, tokens emitidos no mesmo
segundo da revogação são rejeitados conservadoramente; uma emissão nova deve superar o cutoff.

## Persistência histórica e lock de registro

IdentityDeletion recebe Identity, ator, motivo e instante e deve executar snapshot + hard delete na transação corrente.
O histórico permanece uma preocupação de infraestrutura; não é novo aggregate operacional no core.
IdentityRegistrationLock serializa create/approve por chaves naturais normalizadas. O adapter PostgreSQL mantém locks
até o fim da transação. Seus hashes não constituem identificadores de negócio nem dados públicos.

## Proteção HTTP

O mecanismo transversal é descrito em [Platform OpenAPI](../../platform/openapi/docs/README.md). O serviço aceita JWT HS256 com issuer configurado,
sub UUID, iat, exp e permissions. tenant_id é opcional e limita o contexto a um vínculo efetivo.
Identity não emite tokens nem gerencia senha. O operador inicial/token confiável é pré-requisito do ambiente.
O contrato gerado explicita permissões obrigatórias, acesso exclusivo do titular e alternativa titular-ou-permissão.
