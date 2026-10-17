# Estratégia e execução de testes

## Gate completo

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17'
```

O runner cria PostgreSQL 17 em porta aleatória restrita ao localhost, espera readiness, executa clean verify e remove
somente seu próprio container no finally. O build inclui migrations, validação JPA, domínio/aplicação, HTTP real e JAR.
O JAVA_HOME e IDENTITY_TEST_DB_URL do processo são restaurados ao encerrar.

## Recorte por feature

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17' -Tests '*Tenant*Test,*Affiliation*Test'
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17' -Tests '*IdentityApplication*Test,*ApproveIdentityApplication*Test'
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17' -Tests '*SelfDelete*Test'
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17' -Tests '*OpenApi*Test,*Swagger*Test'
```

Para PostgreSQL de teste já provisionado, configure IDENTITY_TEST_DB_URL e execute o Maven diretamente. As credenciais
de teste são identity_test/identity_test. **Use somente banco descartável**: os fixtures truncam as tabelas entre cenários.

## Organização

| Camada | O que verificar |
| --- | --- |
| core/domain/identity, tenant, affiliation | Invariantes, transições e normalização sem Spring |
| core/application/identity, identityapplication | Orquestração e efeitos por ports mockados |
| infra/persistence/identity, identityapplication, affiliation | Migrations, mappers, constraints, concorrência e rollback real |
| api/contract/identity, identityapplication, tenant | HTTP, serialização, validação, status e segurança por feature |
| api/contract/openapi | Contrato gerado de cada endpoint, exemplos válidos, permissões e assets Swagger |
| feature | Fluxo composto de onboarding, ativação e exclusão |
| support | Fixtures compartilhados e setup; não contém testes escondidos |

O detalhamento semântico por feature está em [testes de Identity](../../src/main/java/br/com/imarui/ima/identity/docs/testing.md).
Uma execução focada não substitui o gate completo da árvore final. Consulte XMLs do Surefire em target/surefire-reports
para somar tests/failures/errors/skipped; contagem de métodos fonte não equivale a casos executados de testes parametrizados.
