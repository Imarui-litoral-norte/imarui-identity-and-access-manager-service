# Imarui Identity and Access Manager Service

Java 17 / Spring Boot service. The completed `identity` module owns Identity, Tenant,
IdentityApplication and Affiliation. Authentication and Authorization will evolve on these contracts.

## Run

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for PostgreSQL and `JWT_SECRET` (at least 32 UTF-8 bytes).
Optionally set `JWT_ISSUER` (default `imarui-iam-service`) and `APP_BASE_URL`.
Run `./mvnw spring-boot:run` or `mvnw.cmd spring-boot:run` on Windows.
Flyway creates a fresh schema; Hibernate validates it. Swagger UI: `/swagger-ui/index.html`.
Existing populated schemas require deliberate baseline review; see [Identity operations](docs/identity.md).

## Verify

With Java 17 and Docker running, execute:

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\path\to\jdk-17'
```

The script creates a localhost-only disposable PostgreSQL 17, runs `clean verify`,
and removes its own container. It does not connect to application databases.
With a separately provisioned **disposable test database**, set `IDENTITY_TEST_DB_URL`
and run `mvnw.cmd clean verify`; test credentials are `identity_test` / `identity_test`.
Integration tests truncate this isolated database between cases.

See [Identity contracts and integration](docs/identity.md) and [Jira acceptance mapping](docs/identity-acceptance.md).
