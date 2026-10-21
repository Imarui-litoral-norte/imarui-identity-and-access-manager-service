# Operação segura do Authentication

## Configuração

| Variável/configuração | Uso |
|---|---|
| DB_URL, DB_USERNAME, DB_PASSWORD | PostgreSQL do ambiente; nunca versionar valores reais |
| JWT_PRIVATE_KEY_FILE | PEM RSA PKCS8, mínimo 2048 bits; recomendado 3072; obrigatório fora de test/dev |
| JWT_VERIFICATION_KEY_FILES | Lista de PEMs públicos X509 antigos, separada por vírgula |
| JWT_ISSUER, JWT_AUDIENCE | Emissor e audiência mínima; não aceitar audience arbitrária de request |
| AUTH_ALLOWED_ORIGINS | Origens completas autorizadas; lista separada por vírgula; vazio nega CORS cross-origin |
| AUTH_COOKIE_SAME_SITE | Lax por padrão; Strict/None conforme topologia verificada |
| AUTH_REQUIRE_HTTPS | true por padrão; manter true em ambiente real |
| ACTIVATION_INVITATION_TTL, PASSWORD_RECOVERY_TTL | Convites 24h e recovery 15m por padrão; positivos, no máximo sete dias |
| security.auth.bcrypt-cost | 12 por padrão; testes isolados usam 4 para desempenho |
| security.auth.limits.<operation> | Limites por janela de cinco minutos, buckets PostgreSQL por origem/identificador |
| security.auth.workers-enabled | Polling de revogação/notificações; true por padrão |
| security.auth.frontend-url | URL HTTPS confiável da interface; usada em links sem aceitar URLs do request |
| spring.mail.host/port/username/password | SMTP real do ambiente |
| spring.mail.properties.mail.smtp.starttls.enable | Configurar TLS SMTP conforme provedor |

Operações de throttle: login-origin/login-account (30), refresh-origin/refresh-account (60), activation-origin/activation-account e recovery-origin/recovery-account (5), challenge-origin/challenge-account (30). O bloqueio da Credential ocorre após cinco falhas de senha e dura 15 minutos. Buckets persistem entre reinicializações; nenhum Redis é obrigatório.

As durações definitivas são access 900s e sessão/refresh sete dias. As propriedades legadas de TTL foram alinhadas a 7d e o emissor antigo também limita o refresh ao restante da sessão. Não usar ddl-auto=update.

Mesmo na mesma máquina/IP, habilitar HTTPS para cookies Secure. Usar TLS diretamente no Spring ou TLS nativo. `server.forward-headers-strategy=none` impede interpretação de Forwarded/X-Forwarded-* enviados pelo cliente. A futura IIAMS-80 definirá proxies confiáveis e terminação TLS; não desligar AUTH_REQUIRE_HTTPS para acomodar proxy ainda não homologado. A configuração Hostinger/domínio/certificado deve ser validada quando o ambiente existir; esta rodada não faz deploy.

## Chaves

Gerar fora do repositório, por exemplo com OpenSSL:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /run/secrets/iiam-private.pem
```

Restringir leitura ao processo IIAM. Para rotação, configurar nova privada e adicionar a pública antiga em JWT_VERIFICATION_KEY_FILES; reiniciar de forma controlada. Publicar as duas públicas no JWKS durante a sobreposição. Retirar a antiga somente depois de expiração dos tokens e caches. Chaves efêmeras são permitidas exclusivamente nos perfis test/dev e invalidam tokens após reinício. Não distribuir privadas a consumidores.

## Bootstrap ROOT de uso único

O catálogo sincroniza somente permissions conhecidas do IIAM. ROOT recebe vínculos RolePermission persistidos, como solicitado posteriormente pelo usuário. O bootstrap não cria senha fixa, bypass HTTP ou token master.

1. Preparar descritor local, fora do repositório, por exemplo `{ "kind":"SERVICE", "nickname":"initial-operator", "name":"Initial operator", "email":"operator@example.test" }`. PERSON exige name/cpf/email; LEGAL_ENTITY exige name/cnpj/email; SERVICE também exige e-mail válido. O endereço não precisa ser exclusivo; não confere privilégio.
2. Iniciar uma vez com `--security.bootstrap.enabled=true --security.bootstrap.input-file=<descritor> --security.bootstrap.output-file=<arquivo-novo>`.
3. O processo cria Identity PENDING e associação ROOT em transação única e escreve convite em arquivo novo com ACL/permissão restrita ao proprietário. Não imprime segredo. Arquivo existente não é sobrescrito. Falha remove o arquivo criado e reverte a transação.
4. Apresentar o convite ao endpoint público de ativação e definir senha. Só então a Identity fica ACTIVE e suas authorities passam a funcionar.
5. Remover a flag de bootstrap e apagar os arquivos locais de convite/descritor após entrega segura. Repetição é recusada pelo marcador persistido `authentication_bootstrap`.

Se perder um convite antes da ativação, usar outra Identity operadora já autorizada para reemitir. Se não houver operador ativo, executar recuperação local auditada pelo responsável pelo ambiente; não apagar o marcador ou criar um token master como procedimento automático.

## Notificações e retry

SMTP é o adapter real fora de test/dev. O adapter legado que logava e-mail/link Jeep foi removido. Notificações locais são silenciosas; testes substituem a porta explicitamente.

Jobs persistem IdentityId, finalidade, estado, contador, próxima tentativa e datas finais; nunca tokens em claro. Estados: PENDING, PROCESSING, DELIVERED, FAILED e CANCELLED. O índice parcial permite somente um job PENDING/PROCESSING por Identity/finalidade; FAILED/DELIVERED não bloqueiam uma nova solicitação, respeitando rate limit.

São **cinco tentativas totais**, contando a primeira. A reserva incrementa e confirma o contador antes do SMTP; uma queda pode consumir uma tentativa sem envio, mas nunca autoriza uma sexta. Backoff após cada reserva: 30s, 120s, 600s, 1800s e 3600s. Na quinta falha confirmada, FAILED é gravado imediatamente. Se houver queda na quinta reserva, o próximo processamento após o prazo marca FAILED sem chamar SMTP. Nenhum replay automático reinicia contador terminal.

Cada worker mantém um advisory lock PostgreSQL de sessão em uma conexão dedicada durante reserva, envio e confirmação. A reserva/atualização usa outra conexão e row lock transacional. Assim, um envio lento não concorre com outro worker mesmo após o prazo de reserva; a queda libera o lock e o estado persistido permite retomada. Dimensionar o pool para pelo menos duas conexões por worker ativo, além das requisições normais. O SMTP possui timeouts de conexão/leitura/escrita de cinco segundos.

O worker confirma o hash de um novo challenge antes de enviar e mantém o segredo somente em memória. Link A não é invalidado por emissão/envio/falha do link B. Qualquer link não expirado pode concluir; o consumo bem-sucedido invalida todos da mesma Identity/finalidade na mesma transação da senha/ativação e revogação de sessões. Finalidades diferentes são isoladas. Queda após entrega antes da confirmação pode provocar reenvio; todos os links permanecem seguros por uso único e consumo serializado pela Identity.

Monitorar FAILED, tentativas, finished_at e eventos NOTIFICATION_FAILED/RETRY/DELIVERED. Para nova entrega após falha terminal, solicitar novo job pela API normal; preservar o registro anterior, sem zerar contador manualmente.

Nenhuma mensagem ou exceção SMTP com conteúdo sensível é logada. Validar templates e entrega com o provedor de homologação antes de liberar notificações reais. Esta validação externa não foi executada nesta rodada.

## Migrations e compatibilidade

| Migration | Mudança |
|---|---|
| V6 | Nickname em Identity/IdentityApplication, constraints e índices por kind; contato opcional somente para SERVICE |
| V7 | Credentials, challenges por hash, auditoria, rate limits, jobs de notificação e marcador de bootstrap |
| V8 | IdentityId/SID/revokedAt na Session, encerramento de sessões legadas sem dono, índices e FKs internas de refresh/linhagem |
| V9 | Catálogo Authorization, Role, RolePermission e concessão IdentityRole PLATFORM básica |
| V10 | E-mail/nickname obrigatórios, remoção dos dois índices únicos de e-mail, check de nickname em Application, auditoria ator/alvo/resultado/origem, estados/índices da fila |

**V1–V9 não foram alteradas.** Sem prova suficiente de que V6–V9 jamais foram aplicadas em ambiente persistente compartilhado, foi escolhida V10 incremental, evitando reescrever checksums publicados. A inspeção disponível foi restrita ao checkout e bancos Docker isolados; nenhum ambiente compartilhado/produção foi consultado. Aplicar primeiramente em banco de homologação/cópia validada, com backup e inspeção dos dados; a aplicação utiliza Flyway e Hibernate validate. Nenhuma migration foi aplicada em produção.

Não existem Identities legadas a regularizar nesta entrega. Nickname e e-mail são NOT NULL; não existe backfill nem criação de Identity/nickname artificial. Antes de aplicar V10 em qualquer ambiente futuro, executar somente nesse ambiente autorizado o preflight: contar Identities/Applications com nickname ou contato ausentes e inspecionar flyway_schema_history. O esperado é zero. Se houver dados inesperados incompletos, interromper e revisar; V10 falha transacionalmente, não corrige dados por suposição. E-mail compartilhado é permitido; CPF/CNPJ e nickname por kind mantêm seus índices únicos.

Sessões antigas sem IdentityId ficam REVOKED com histórico preservado por V8. Credential/challenge e concessões operacionais usam FK Identity com cascata; Session/auditoria preservam UUIDs históricos sem FK operacional. V10 converte jobs antigos com delivered_at em DELIVERED e jobs esgotados (attempts >= 5) em FAILED, preservando inclusive contadores históricos acima de cinco. Novo processamento nunca envia uma sexta tentativa.

Não reverter essas migrations apagando tabelas/dados. Em caso de falha, interromper a aplicação, preservar o banco e aplicar correção incremental ou restaurar backup testado conforme decisão operacional.

## Revogação, monitoramento e retenção

O consumidor marca processedAt somente na mesma transação dos efeitos. Falha deixa a solicitação pendente; retry é idempotente. Revogação Identity usa cutoff e não afeta sessões criadas depois da solicitação. TENANT mantém cutoff no Identity, não encerra indiscriminadamente sessões globais e continua rejeitando contexto desativado após reativação. JWT global V1 não concede contexto Tenant.

Monitorar contagem/idade de pedidos pendentes, jobs FAILED ou PROCESSING vencidos, eventos LOGIN_FAILED/REFRESH_REPLAY/PASSWORD_RESET e a métrica `iiam.authentication.worker.failures`. Métricas não usam nickname, documento ou tokens como tags. Limitar acesso a actuator/diagnósticos na infraestrutura.

Nenhuma rotina destrutiva de retenção foi ativada. Preservar hashes de tokens rotacionados até o término absoluto da sessão para detectar replay. Limpeza posterior pode remover challenges expirados e sessões encerradas após a política de retenção definida pelo responsável; não remover cutoffs enquanto tokens/sessões anteriores puderem existir. Histórico Identity/Affiliation não é alterado por limpeza Authentication.

## Convites administrativos e origem

ADMIN_INVITATION registra actor_identity_id, target_identity_id, resultado SUCCESS/DENIED, occurred_at UTC e request_origin da conexão. Não há infraestrutura de correlação existente; não foi criado um cabeçalho confiável artificial. Justificativa não é exigida. Solicitações públicas geram eventos distintos. Nunca registrar resposta de convite, senha, hash de senha ou tokens em logs/reverse proxies.

## Integração futura

[IIAMS-80](https://logimarui.atlassian.net/browse/IIAMS-80) trata Gateway, prefixos, TLS, sanitização de forwarded headers, cookies/CSRF e supervisor. Não foi implementada nem iniciada; não bloqueia estabilização standalone. Nenhum arquivo de logimarui-core-api foi alterado.
