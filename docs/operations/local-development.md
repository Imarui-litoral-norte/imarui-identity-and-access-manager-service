# Ambiente local e inicialização

Requisitos: Java 17, Maven Wrapper do repositório e PostgreSQL. Docker é usado pelo runner de testes isolados.

| Variável | Uso |
| --- | --- |
| DB_URL | JDBC URL do PostgreSQL da aplicação |
| DB_USERNAME / DB_PASSWORD | Credenciais do banco |
| JWT_SECRET | Material de assinatura HS256 com pelo menos 32 bytes UTF-8 |
| JWT_ISSUER | Issuer confiável; padrão imarui-iam-service |
| APP_BASE_URL | URL externa do serviço; padrão http://localhost:8080 |
| SESSION_TTL / REFRESH_TOKEN_TTL | Configuração dos componentes existentes de Authentication; padrões 12h e 30d |
| PASSWORD_RECOVERY_TTL / PASSWORD_CHANGE_CHALLENGE_TTL | Configuração existente; padrões 15m e 5m |

Forneça secrets pelo ambiente. `application-local.yaml.example` é uma referência sem credenciais válidas; o arquivo local
real é ignorado pelo Git. Flyway migra e Hibernate valida o schema. Para banco já existente, siga a [política de banco](../database/README.md).

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
# Configure DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET e JWT_ISSUER no ambiente.
.\mvnw.cmd spring-boot:run
```

Após inicializar, `/docs` abre a documentação personalizada e `/v3/api-docs` entrega a especificação. `/swagger-ui/index.html`
permanece disponível como interface padrão. Os assets são servidos pelo próprio serviço.
A criação de solicitação é pública. Operações protegidas exigem token confiável de uma Identity ACTIVE e permissions
coerentes. O operador inicial e seu token precisam ser provisionados pelo responsável pelo ambiente/Authentication;
não existe administrador padrão nem endpoint de login implementado por Identity.

Valide dados, issuer e assinatura no ambiente correto antes de investigar erro de autorização. A regra de ator e
revogação está em [contratos de integração](../../src/main/java/br/com/imarui/ima/identity/docs/integration-contracts.md).
Para executar uma validação completa sem usar o banco da aplicação, siga o [runner de testes](../testing/README.md).
