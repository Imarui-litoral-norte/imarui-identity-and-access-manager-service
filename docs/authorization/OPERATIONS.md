# Operação, migrações e recuperação

Este guia prepara uma integração futura. A Rodada 2 não executa deploy, merge, alterações no
Gateway ou acesso a dados de produção. PostgreSQL de teste é descartável e isolado.

## Antes de atualizar

Conferir versão do aplicativo/schema, Flyway validate e saúde de Identity/Authentication.
Conferir que existe ROOT ACTIVE efetivo com as capacidades de recuperação na fronteira esperada;
um cadastro PENDING não substitui ROOT ativo. Dados legados inconsistentes devem ser investigados,
não reativados ou apagados automaticamente. Fazer backup consistente e ensaiar restauração em
ambiente isolado. Registrar responsáveis, janela, correlação, versão e plano de reversão.

Migrations V1–V17 são preservadas byte a byte. A sequência adicional é:

| Versão | Efeito e compatibilidade |
| --- | --- |
| V18 | Reconcilia DENYs equivalentes ativos; mantém o mais antigo, inativa duplicatas e audita before/after; índices únicos separados por destinatário |
| V19 | Backfill active → ACTIVE/INACTIVE; status explícito, compatibilidade física do boolean e histórico de exclusão de Role |
| V20 | Clients/chaves públicas/nonces/jurisdições/bindings técnicos/jornal de segurança; upgrade explícito das três novas capacidades internas ROOT |
| V21 | Aprovações de transferência ROOT, histórico de Scope, proteção de último ROOT ACTIVE e históricos append-only |
| V22 | Recurso auditável de até 200 caracteres e índices de investigação |
| V23 | Completa primeira delegação de governança interna de ROOT legado; respeita qualquer delegação histórica, inclusive revogada |
| V24 | Criação/reativação de assignment exige Role ACTIVE, inclusive por SQL; índice de consulta por recurso auditado |

V18 não elimina registro: antes de migrar, pode-se inventariar duplicatas agrupando
permission_code, scope_id e identity_id/role_id com active=true. Após migração, consultar
`authorization_audit.action='DUPLICATE_DENY_RECONCILED'`. A reconciliação mantém a negação
original enquanto corrige ambiguidade de revogação. Concessões não são fabricadas para resolver dados.

`active` físico de Role continua projeção de compatibilidade; o aplicativo novo usa `status`.
Não executar binários antigos concorrentes que desconhecem ARCHIVED ou as regras atuais.
Instalação limpa não inventa usuário/assignment: utiliza o bootstrap de Authentication existente.
Upgrade mantém IDs de Role, Permission e RolePermission, UUID do assignment a partir de V13,
sessões, refresh e históricos. Constraints são proteções adicionais, não substituem os casos de uso.

## Reversão e falhas

Flyway comunitário não fornece down migration deste conjunto. Não apagar flyway_schema_history,
não editar V1–V24 aplicadas e não rodar clean contra base operacional. Falha transacional em
mutação ou migration PostgreSQL reverte a transação; os testes verificam rollback, ausência de
ganho parcial e consistência concorrente. Para desfazer comportamento de negócio, usar revogação,
correção de lifecycle ou nova migration revisada. ARCHIVED é terminal; não mudar status por SQL
para contornar a regra. Reversão de versão de aplicativo/schema exige restauração testada do
backup ou correção progressiva compatível, sob autorização operacional. O ensaio desta entrega
valida rollback transacional, não afirma ter restaurado um backup de produção.

## Transferência administrativa e emergência

1. ROOT existente e ativo na fronteira exata autentica-se normalmente e confere o destinatário
   ACTIVE, sua identidade e o Scope. Obter aprovação operacional externa conforme governança local.
2. POST `/api/v1/authorization/admin/root/transfers` com recipientId, scopeId, password,
   reason e correlationId. O iniciador precisa de ROOT_RECOVERY e autoridade efetiva atual.
   Senha só via TLS; não registrar payload. Há no máximo três aprovações pendentes por iniciador.
3. O destinatário consulta o approvalId e verifica iniciador, fronteira, expiração e capacidades.
   POST `/{id}/confirm` com sua própria senha e sessão. Não compartilhar credenciais.
4. Até 10 minutos, ambos devem continuar ativos e com sessões válidas. Confirmação consome a
   aprovação atomicamente, instala ROOT no destinatário e revoga atribuição/delegações do anterior
   naquela fronteira. Uma repetição confirmada é idempotente para o destinatário autorizado.
5. Verificar auditoria ROOT_TRANSFER_REQUESTED, ROOT_TRANSFER_CONFIRMED, ROOT_GOVERNANCE_*
   e, se aplicável, ROOT_CONSTRAINT_PRESERVED. Verificar nova capacidade administrativa e ausência
   de liberação indevida de negócio. Preservar evidência da aprovação externa e correlação.

Não é possível remover o último ROOT efetivo pelas APIs regulares. Um ROOT não pode escapar
de DENY de negócio por transferência ou reparenting. Recuperação de senha utiliza o fluxo
de Authentication existente, com os limites e auditoria daquele módulo. Se não existe sessão
recuperável porque banco/chaves foram perdidos ou os dados estão corrompidos, não há endpoint
de bypass: isolar incidente, preservar logs e restaurar backup conhecido sob controle de dois
responsáveis. Não reexecutar bootstrap para fabricar administrador nem conceder ROOT por SQL
avulso. Reparação excepcional de dados requer plano específico revisado; não foi realizada nesta entrega.

## Chaves técnicas, nonce e limites

Conceder SERVICE_MANAGE apenas no plano de controle e SCOPE_MANAGE para as jurisdições
solicitadas. Gerar chave privada no consumidor, cadastrar pública, limitar operações e validade,
monitorar expiração e rotacionar antes do prazo. Desabilitar cliente ou revogar kid interrompe
novas decisões. Não confiar em host/rede interna como autenticação. Auditar mudanças de client,
chave, jurisdição e binding. Um binding de objeto é estável; mudança de propriedade requer
procedimento explícito revisado, não substituição silenciosa por chamada de consumidor.

JTI é único por credencial no PostgreSQL, inclusive entre instâncias. A implementação remove
nonces expirados no caminho de autenticação; retenção de auditoria é independente e requer
política operacional de retenção/arquivamento que preserve evidências. Não desativar triggers
append-only para contornar erro. Limites por origem/cliente usam o rate limiter persistido existente:
6.000 e 3.000 requisições por cinco minutos, respectivamente; administração humana 900/5 min.
Esses valores são proteção inicial, não promessa de throughput; ajustar com medição e governança.

## Observabilidade e resposta a incidentes

Investigar pelo correlationId e decisionId. Consultar `/admin/audit` com AUDIT_READ no Scope;
subárvores exigem autoridade efetiva em cada fronteira e eventos globais exigem PLATFORM.
Histórico de Scopes excluídos continua consultável sob ancestral autorizado. Consultas explicativas
também são auditadas; a resposta PDP comum não carrega a política completa.

Métricas Micrometer: `iiam.authorization.policy.snapshot`, `iiam.authorization.policy.rows`,
`iiam.authorization.mutation.attempts`, `iiam.pdp.decisions`, `iiam.pdp.duration` e `iiam.m2m.request`.
Verificar nomes efetivos do registry/exporter ao integrar monitoração. Não há exportador externo
novo. Actuator mantém somente health exposto por padrão; se o operador habilitar outros endpoints,
eles exigem AUDIT_READ em PLATFORM. Jamais expor metrics/env indiscriminadamente.

Observar latência de snapshot, contenção do lock 810082, crescimento de política/auditoria e 429/503.
Uma Role amplamente compartilhada torna a checagem de impacto proporcional aos sujeitos/Scopes;
novos índices/cache só devem entrar com evidência e testes de revogação. Não usar réplica atrasada
para decisões. Corpos, senhas, JWTs e chaves privadas não pertencem a logs, traces ou tickets.

## Verificação reproduzível

Usar Java 17 e PostgreSQL isolado, configurar `IDENTITY_TEST_DB_URL` e executar
`./mvnw -B clean verify` (Windows: `mvnw.cmd`). A suíte cria schemas de upgrade independentes e
testa instalação limpa, V10 populado, V17 populado, ROOT e preservação de históricos. Conferir
os XMLs em target/surefire-reports, `target/authorization-openapi.json` e
`target/authorization-benchmark.json`. Não apontar a suíte de integração para base de negócio.
O CI usa PostgreSQL 17; a validação local desta rodada usa PostgreSQL 18.4.
