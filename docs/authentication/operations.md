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
| security.auth.bcrypt-cost | 12 por padrão; testes isolados usam 4 para desempenho |
| security.auth.limits.<operation> | Limites por janela de cinco minutos, buckets PostgreSQL por origem/identificador |
| security.auth.workers-enabled | Polling de revogação/notificações; true por padrão |
| security.auth.frontend-url | URL HTTPS confiável da interface; usada em links sem aceitar URLs do request |
| spring.mail.host/port/username/password | SMTP real do ambiente |
| spring.mail.properties.mail.smtp.starttls.enable | Configurar TLS SMTP conforme provedor |

Operações de throttle: login-origin/login-account (30), refresh-origin/refresh-account (60), activation-origin/activation-account e recovery-origin/recovery-account (5), challenge-origin/challenge-account (30). O bloqueio da Credential ocorre após cinco falhas de senha e dura 15 minutos. Buckets persistem entre reinicializações; nenhum Redis é obrigatório.

As durações definitivas são access 900s e sessão/refresh sete dias. As propriedades legadas de TTL foram alinhadas a 7d e o emissor antigo também limita o refresh ao restante da sessão. Não usar ddl-auto=update.

Mesmo na mesma máquina/IP, habilitar HTTPS para cookies Secure. Usar TLS diretamente no Spring ou proxy confiável com encaminhamento de protocolo estritamente configurado. Não aceitar X-Forwarded-* de redes arbitrárias. A configuração Hostinger/domínio/certificado deve ser validada quando o ambiente existir; esta rodada não faz deploy.

## Chaves

Gerar fora do repositório, por exemplo com OpenSSL:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /run/secrets/iiam-private.pem
```

Restringir leitura ao processo IIAM. Para rotação, configurar nova privada e adicionar a pública antiga em JWT_VERIFICATION_KEY_FILES; reiniciar de forma controlada. Publicar as duas públicas no JWKS durante a sobreposição. Retirar a antiga somente depois de expiração dos tokens e caches. Chaves efêmeras são permitidas exclusivamente nos perfis test/dev e invalidam tokens após reinício. Não distribuir privadas a consumidores.

## Bootstrap ROOT de uso único

O catálogo sincroniza somente permissions conhecidas do IIAM. ROOT recebe vínculos RolePermission persistidos, como solicitado posteriormente pelo usuário. O bootstrap não cria senha fixa, bypass HTTP ou token master.

1. Preparar descritor local, fora do repositório, por exemplo `{ "kind":"SERVICE", "nickname":"initial-operator", "name":"Initial operator" }`. PERSON exige name/cpf/email; LEGAL_ENTITY exige name/cnpj/email; SERVICE admite contato ausente.
2. Iniciar uma vez com `--security.bootstrap.enabled=true --security.bootstrap.input-file=<descritor> --security.bootstrap.output-file=<arquivo-novo>`.
3. O processo cria Identity PENDING e associação ROOT em transação única e escreve convite em arquivo novo com ACL/permissão restrita ao proprietário. Não imprime segredo. Arquivo existente não é sobrescrito. Falha remove o arquivo criado e reverte a transação.
4. Apresentar o convite ao endpoint público de ativação e definir senha. Só então a Identity fica ACTIVE e suas authorities passam a funcionar.
5. Remover a flag de bootstrap e apagar os arquivos locais de convite/descritor após entrega segura. Repetição é recusada pelo marcador persistido `authentication_bootstrap`.

Se perder um convite antes da ativação, usar outra Identity operadora já autorizada para reemitir. Se não houver operador ativo, executar recuperação local auditada pelo responsável pelo ambiente; não apagar o marcador ou criar um token master como procedimento automático.

## Notificações e retry

SMTP é o adapter real fora de test/dev. O adapter legado que logava e-mail/link Jeep foi removido. Notificações locais são silenciosas; testes substituem a porta explicitamente.

Jobs persistem apenas IdentityId, finalidade e metadados; tokens em claro não ficam na fila. O worker reserva o job, gera segredo em memória e confirma hash/challenge antes de enviar. Falha deixa o job reagendado em cinco minutos; retry gera novo convite e invalida o anterior. Limite de dez tentativas. Queda após envio e antes do acknowledge pode causar reenvio com novo link, mas nunca dois challenges da mesma finalidade válidos. SMTP não participa da transação de senha/Identity.

Nenhuma mensagem ou exceção SMTP com conteúdo sensível é logada. Validar templates e entrega com o provedor de homologação antes de liberar notificações reais. Esta validação externa não foi executada nesta rodada.

## Migrations e compatibilidade

| Migration | Mudança |
|---|---|
| V6 | Nickname em Identity/IdentityApplication, constraints e índices por kind; contato opcional somente para SERVICE |
| V7 | Credentials, challenges por hash, auditoria, rate limits, jobs de notificação e marcador de bootstrap |
| V8 | IdentityId/SID/revokedAt na Session, encerramento de sessões legadas sem dono, índices e FKs internas de refresh/linhagem |
| V9 | Catálogo Authorization, Role, RolePermission e concessão IdentityRole PLATFORM básica |

V1–V5 não foram alteradas. Aplicar primeiramente em banco de homologação/cópia validada, com backup e inspeção dos dados; a aplicação utiliza Flyway e Hibernate validate. Nenhuma migration foi aplicada em produção.

Linhas legadas sem nickname permanecem legíveis, mas não autenticam até regularização; novos contratos HTTP exigem nickname. Não inventar nickname de produção. Sessões legadas sem IdentityId ficam REVOKED com histórico preservado. Credential/challenge e concessões operacionais usam FK Identity com remoção em cascata para não bloquear exclusão física. Session/auditoria mantêm o UUID como valor histórico sem FK operacional.

Não reverter essas migrations apagando tabelas/dados. Em caso de falha, interromper a aplicação, preservar o banco e aplicar correção incremental ou restaurar backup testado conforme decisão operacional.

## Revogação, monitoramento e retenção

O consumidor marca processedAt somente na mesma transação dos efeitos. Falha deixa a solicitação pendente; retry é idempotente. Revogação Identity usa cutoff e não afeta sessões criadas depois da solicitação. TENANT mantém cutoff no Identity, não encerra indiscriminadamente sessões globais e continua rejeitando contexto desativado após reativação. JWT global V1 não concede contexto Tenant.

Monitorar contagem/idade de pedidos pendentes, jobs com attempts=10, eventos LOGIN_FAILED/REFRESH_REPLAY/PASSWORD_RESET e a métrica `iiam.authentication.worker.failures`. Métricas não usam nickname, documento ou tokens como tags. Limitar acesso a actuator/diagnósticos na infraestrutura.

Nenhuma rotina destrutiva de retenção foi ativada. Preservar hashes de tokens rotacionados até o término absoluto da sessão para detectar replay. Limpeza posterior pode remover challenges expirados e sessões encerradas após a política de retenção definida pelo responsável; não remover cutoffs enquanto tokens/sessões anteriores puderem existir. Histórico Identity/Affiliation não é alterado por limpeza Authentication.
