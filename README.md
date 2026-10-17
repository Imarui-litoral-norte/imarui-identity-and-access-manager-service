# Imarui Identity and Access Manager Service

Serviço IAM do ecossistema Imaruí, desenvolvido em Java 17 e Spring Boot.
O módulo Identity implementa cadastro e ciclo de vida de identidades, solicitações de onboarding,
Tenants, vínculos e exclusão própria auditada. Authentication e Authorization evoluem sobre esses contratos.

## Arquitetura e documentação

Comece pelo [índice documental](docs/README.md). Cada nível possui seu conteúdo:

- [Arquitetura global](docs/architecture/README.md): camadas, ownership e regras de desenvolvimento.
- [Módulo Identity](src/main/java/br/com/imarui/ima/identity/docs/README.md): regras, estados, fluxos e integração.
- [Testes por feature](docs/testing/README.md): suites, cenários e execução isolada.
- [Ambiente local](docs/operations/local-development.md) e [banco](docs/database/README.md): configuração e migrations.
- [Aceite de Identity](docs/identity-acceptance.md): critérios e evidências executadas.

## Tecnologias

Java 17, Spring Boot 4.1, Spring Security, Spring Data JPA, PostgreSQL, Flyway,
Maven Wrapper e springdoc OpenAPI. Redis e Mail permanecem dependências dos componentes existentes.

## Execução

Configure DB_URL, DB_USERNAME, DB_PASSWORD e JWT_SECRET (pelo menos 32 bytes UTF-8).
JWT_ISSUER e APP_BASE_URL têm defaults documentados no guia de operação. Forneça um operador/token confiável
para acessar operações protegidas; Identity não cria administrador padrão nem emite tokens de login.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
.\mvnw.cmd spring-boot:run
```

Flyway migra um schema novo e Hibernate valida o modelo. Bancos populados anteriores ao Flyway exigem
comparação e baseline deliberados conforme o [procedimento de banco](docs/database/README.md).

## Documentação HTTP

Com o serviço iniciado, abra `/docs`: Swagger personalizado com módulos e grupos por feature,
descrições de negócio, requests/responses, exemplos, regras de acesso e erros RFC 9457.
A especificação é `/v3/api-docs`; a interface padrão segue em `/swagger-ui/index.html`.
Os assets são locais e a UI não persiste autorização no navegador.

## Validação

Com Java 17 e Docker disponíveis:

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17'
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17' -Tests '*OpenApi*Test,*Swagger*Test'
```

O runner usa PostgreSQL descartável, aplica migrations, executa clean verify e remove seu container.
Os testes estão separados por feature e camada, com fixtures em support. Consulte o
[guia de testes](docs/testing/README.md) para outros recortes e para um banco de teste provisionado.

## Estrutura

```text
docs/                      arquitetura, operação, banco, testes e evidências globais
scripts/                   execução reproduzível da validação
src/main/java/.../identity  api, core, infra e docs internos
src/main/java/.../authentication
src/main/java/.../platform  composição técnica, segurança e OpenAPI
src/main/resources/static/openapi-custom
src/test/java/.../identity  cenários por camada e feature
```
