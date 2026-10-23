# Authorization do IIAM

O IIAM decide autorização; o serviço que possui o recurso aplica a decisão. Identity possui
Identities, Tenants e Affiliations. Authentication possui senhas, sessões, revogações e credenciais
técnicas. Authorization possui Scopes, catálogo, Roles, atribuições, DENYs, delegações e auditoria.
A composição HTTP/JWT fica em Platform. Nenhuma habilitação de aplicação por Tenant foi adicionada.

## Política e fronteiras

`EffectiveAuthorizationEvaluator` é o algoritmo único. A revisão corrente contém Scopes,
estado das Identities/Tenants, Roles, composição, assignments, DENYs e delegações. Não se exige
Affiliation para uma atribuição válida. O JWT identifica sujeito e sessão; não é fonte de Permissions.

1. Verificar Identity, Permission e Scope efetivos; estados inativos invalidam a concessão.
2. Resolver atribuições válidas nos ancestrais, respeitando ownership e disponibilidade da Role.
3. Aplicar DENYs de Identity e das Roles efetivas, diretos ou herdados, antes dos ALLOWs.
4. Conceder somente por Role ativa com Permission cadastrada; ausência de concessão resulta em default deny.

DEPRECATED preserva uso por concessões existentes e impede novas concessões/delegações,
conforme a política da fundação; depreciação não é revogação. Para revogar uso, alterar RolePermission,
assignment, DENY ou lifecycle. O código persistido e seu histórico não são apagados pelo manifesto.

Scopes possuem UUID próprio, código estável, pai e estado. O UUID do Tenant é distinto do UUID
do Scope; o binding persistido é estável. Árvores independentes não herdam de PLATFORM.
Reparenting mantém o UUID e valida atribuições, ownership, ganhos de uso/delegação, DENYs que
deixariam de ser herdados e recuperabilidade. Ramos inativos também entram na análise de ganho potencial.

## Roles, assignments e delegação

GLOBAL define disponibilidade global, sem conceder uso automaticamente. SCOPE pertence a uma
fronteira e fica disponível nela e em descendentes. TENANT pertence ao Tenant e ao seu Scope.
Disponibilidade para atribuição não concede administração do proprietário da Role.

O lifecycle é explícito: ACTIVE ↔ INACTIVE; ACTIVE/INACTIVE → ARCHIVED quando não há assignment
ativo. ARCHIVED é terminal. INACTIVE retém assignments e suspende sua eficácia; novas atribuições
exigem ACTIVE. Exclusão física exige ARCHIVED e ausência de qualquer assignment operacional,
inclusive revogado, preservando o histórico independente de definição, composição e DENYs.
ROOT tem proteção adicional e não segue o CRUD comum. IDs existentes são preservados.

Uso, administração e delegação são direitos distintos. `PermissionDelegation` especifica sujeito,
Permission, Scope e possibilidade de redelegar. Permission administrativa sozinha não autoriza
distribuir toda Permission disponível. Guards verificam ganhos de uso, delegação e redelegação
em cada Scope afetado, inclusive sujeitos com somente delegação, Roles compartilhadas e
self-grant indireto. As capacidades centrais exigem governança central ou
ROOT efetivo da própria raiz, com autoridade granular válida; um administrador local não clona ROOT.

## DENY e histórico

Existe no máximo um DENY ativo por Permission + Scope + destinatário Identity ou Role. Repetir
a criação equivalente retorna o mesmo ID; os dois tipos de destinatário têm índices únicos parciais
separados. A revogação exige versão atual. Criar novamente após revogação produz nova geração/UUID;
o registro revogado não é reativado implicitamente. Domínio, lock e PostgreSQL protegem concorrência.
V18 reconcilia duplicatas legadas mantendo o registro ativo mais antigo e auditando a inativação
dos demais, sem apagar registros nem motivos.

## Catálogo federado

Um owner cadastrado controla namespace próprio. O manifesto completo versionado contém metadados
de Permissions; o digest canônico torna repetição idêntica idempotente. Reutilizar versão com conteúdo
diferente, regredir versão ou invadir namespace alheio falha. Omissão de código deprecia e preserva
IDs/referências/histórico. Sincronização externa exige CATALOG_SYNC do mesmo serviço autenticado.
Publicar catálogo não concede uso ou delegação; a primeira autoridade exige aprovação explícita
por raiz. Manifesto interno v2 e V20/V23 fazem somente o upgrade explícito das capacidades internas
de recuperação de ROOT, sem grants de catálogo externo nem ressurreição de delegações revogadas.

## PDP, PEP e M2M

```mermaid
sequenceDiagram
  participant U as Usuário
  participant P as Serviço PEP
  participant A as Authentication
  participant D as PDP IIAM
  participant B as PostgreSQL
  U->>P: Recurso + JWT humano
  P->>P: Resolver propriedade real do recurso
  P->>D: Prova M2M assinada + JWT humano + Permission + recurso
  D->>A: Validar serviço, prova única e sessão humana
  A->>B: Credencial, nonce, sessão e revogações
  D->>B: Snapshot atual de política e jurisdição
  D->>D: Evaluator único, DENY antes de ALLOW
  D->>B: Auditoria da decisão na mesma transação
  D-->>P: Decisão mínima + ID + correlação
  P-->>U: Aplicar decisão; falha/timeout nega
```

A identidade técnica tem client UUID independente de Identity humana; chave pública RSA pertence
ao serviço cadastrado e tem validade, fingerprint e revogação. A chave privada fica exclusivamente
no consumidor. Prova por requisição usa RS256, typ `iiam-service+jwt`, kid UUID, iss/sub do serviço,
audience própria, exp ≤ 60 s, jti UUID de uso único, método, path, SHA-256 do corpo UTF-8 e correlação
assinada. Não é apresentado como OAuth/DPoP padronizado. Há rotação com sobreposição e operações
PDP_DECIDE/CATALOG_SYNC independentes. Detalhes no [guia do consumidor](CONSUMER_GUIDE.md).

O PDP revalida credencial/jurisdição dentro do mesmo snapshot da política, valida JWT e sessão
humana e exige Actor igual ao subject. A Permission deve pertencer ao serviço autenticado.
TENANT resolve Scope pelo binding oficial; outros objetos exigem binding técnico previamente
aprovado. `expectedScopeId` só é uma asserção, nunca fonte de autoridade. O serviço continua
responsável por resolver o Tenant real do objeto que possui; o IIAM não consulta o banco privado
do consumidor. Batch de até 20 itens mantém um único Actor/JWT/correlação e snapshot.

## ROOT e recuperação

ROOT continua sujeito aos DENYs comuns. O envelope protegido contém nove capacidades de segurança:
AUTHORIZATION_SCOPE_MANAGE, ROLE_MANAGE, ASSIGNMENT_MANAGE, DENY_MANAGE, DELEGATION_MANAGE,
CATALOG_MANAGE, ROOT_RECOVERY, AUDIT_READ (as oito com prefixo AUTHORIZATION_) e
AUTHENTICATION_SERVICE_MANAGE. Não são Permissions de negócio nem um bypass geral.

O último ROOT efetivo não pode ser removido por Identity, Role, assignment, Scope ou DENY;
um convite PENDING não substitui administrador ACTIVE. A transferência exige ROOT existente na
fronteira exata, sessão humana atual e reautenticação do iniciador; destinatário distinto e ativo
confirma com sua própria sessão/senha em até 10 minutos. Revalidam-se sessões, delegação,
ganhos e recuperação sob lock. O novo assignment é instalado antes da revogação do anterior,
na mesma transação. DENYs de negócio que seriam contornados pela transferência são preservados
como restrições explícitas, inclusive Permissions apenas negadas pela Role ROOT e restauração
de delegação sem ALLOW de uso. Motivo, correlação e ambos os sujeitos ficam auditados.
Pedidos e confirmações emitem WARN correlacionado e métrica somente após commit.

Não existe endpoint anônimo de criação de ROOT. Recuperação da credencial humana reutiliza o
fluxo de Authentication; perda catastrófica de banco/chaves exige restauração controlada do backup.
Ver [operação](OPERATIONS.md), incluindo limites do procedimento de emergência.

## Consistência, auditoria e escala

Mutações usam o lock transacional PostgreSQL 810082 antes de ler a política e validar versões.
O contexto humano é revalidado no ponto de uso depois do lock, incluindo JWT, SID e revogações;
não se mantém autorização de sessão apenas porque o filtro HTTP a verificou antes da fila.
Leituras de política usam snapshot REPEATABLE READ; novas decisões após commit veem revogação
sem novo login. Não há cache entre requisições. Chamadas já em avaliação podem concluir na revisão
consistente anterior: revogação não retroage sobre decisão concluída. O PEP deve consultar junto
da operação e não guardar ALLOW entre operações; uma transação do recurso externo não é atômica
com o banco do IIAM.

Auditoria administrativa inclui before/after, ator, recurso, Scope, motivo, instante e correlação.
O jornal técnico inclui serviço, decisão e resultado mínimo, sem JWT/senha/chave privada.
ALLOW depende do commit do evento de decisão na mesma transação. Falha parcial reverte a mutação;
negativas são registradas separadamente após rollback. Históricos/auditorias são append-only no
papel SQL de aplicação e não dependem da permanência física de Identity/Role/Scope excluídos.
O superusuário PostgreSQL pertence ao controle operacional e pode alterar estruturas; não é
uma fronteira que triggers pretendam conter.

Métricas medem snapshot, volume carregado, decisão, chamadas M2M e tentativas de mutação, sem
labels com sujeitos, tokens ou UUIDs. Árvores são reutilizadas apenas na revisão corrente.
A [medição F-02](f02-benchmark.json) mantém o desenho simples: snapshot p95 12,4119 ms com
2.365 Scopes; uma edição compartilhada levou 887,035 ms. É um cenário sintético isolado,
não uma garantia de capacidade; medir crescimento e contenção antes de adotar cache/indexação.
