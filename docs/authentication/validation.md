# Evidência de validação — IIAMS-39

Execução final local em 21/10/2026, concluída às 15:26:13 America/Sao_Paulo.

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\Users\lucas.l\.jdks\ms-17.0.18'
```

O script criou PostgreSQL 17.11 isolado, aguardou readiness, executou Maven `-B clean verify` com Java 17.0.18 e removeu o container ao final. Flyway aplicou V1–V9; Hibernate validou as entidades. Resultado: **154 testes, 0 falhas, 0 erros, 0 skips; BUILD SUCCESS**, 59.107 s, JAR executável gerado.

JAR SHA-256: `b0b4ac5bde6f6fcb71da5d76ff8fddf083bac0ed1390bfed98a7f92b91c6dea2`.

Detalhamento parseado dos relatórios Surefire: [test-results.json](test-results.json). A execução inicial tinha 118 testes; foram preservados seus cenários, adaptando fixtures de ativação e assinatura para o fluxo real, e adicionados 36 cenários.

| Suíte | Testes | Resultado |
|---|---:|---|
| br.com.imarui.ima.authentication.AuthenticationServiceApplicationTests | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.authentication.core.AuthenticationDomainTest | 5 | sem falhas/erros/skips |
| br.com.imarui.ima.authentication.core.JwtKeyRotationTest | 2 | sem falhas/erros/skips |
| br.com.imarui.ima.authentication.core.SmtpChallengeMailTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.authentication.feature.AuthenticationIntegrationTest | 27 | sem falhas/erros/skips |
| br.com.imarui.ima.authentication.feature.MigrationUpgradeIntegrationTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.contract.identity.IdentityHttpContractTest | 3 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.contract.identity.IdentitySelfDeleteHttpContractTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.contract.identityapplication.IdentityApplicationHttpContractTest | 9 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.contract.openapi.IdentityOpenApiIntegrationTest | 20 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.contract.openapi.IdentitySwaggerUiIntegrationTest | 2 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.contract.tenant.TenantHttpContractTest | 2 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.api.dto.identityapplication.response.IdentityApplicationResponseTest | 3 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.application.identity.IdentityLifecycleUseCaseTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.application.identity.SelfDeleteIdentityUseCaseTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.application.identityapplication.ApproveIdentityApplicationUseCaseTest | 5 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.domain.affiliation.AffiliationDomainTest | 7 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.domain.affiliation.AffiliationValidityTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.domain.identity.IdentityDomainTest | 5 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.domain.identity.IdentityLifecycleTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.core.domain.tenant.TenantDomainTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.feature.IdentityEndToEndTest | 1 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.infra.persistence.affiliation.AffiliationHistoryIntegrationTest | 24 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.infra.persistence.affiliation.AffiliationIntegrationTest | 7 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.infra.persistence.identity.IdentityRevocationIntegrationTest | 3 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.infra.persistence.identity.IdentitySelfDeleteIntegrationTest | 5 | sem falhas/erros/skips |
| br.com.imarui.ima.identity.infra.persistence.identityapplication.IdentityApplicationIntegrationTest | 12 | sem falhas/erros/skips |
| br.com.imarui.ima.platform.openapi.SwaggerOperationCustomizersTest | 3 | sem falhas/erros/skips |

## Verificações complementares

- `git diff --check` sem erros de whitespace.
- V1–V5 comparadas com a base main: sem mudanças.
- Teste real de upgrade V5 com Session/Refresh legados, preservação de registros e revogação sem proprietário artificial.
- Concorrência PostgreSQL para nickname, challenge, refresh e disable versus refresh.
- Falhas deliberadas por trigger testam rollback de ativação e de processamento da outbox; retry confirma o efeito integral.
- HTTP completo PERSON/LEGAL_ENTITY/SERVICE; cookies, CSRF, CORS, logout e refresh, além de regressão Identity.
- JWT errado/expirado, issuer/audience, assinatura, SID/UUID, chaves rotacionadas, JWKS somente público e ausência de privilégios concedidos por claims.
- SMTP adapter exercitado contra servidor SMTP local isolado, com template IIAM e link HTTPS correto. Nenhuma mensagem foi enviada a destinatário externo.
- Build de CI configurado em `.github/workflows/verify.yml`, com PostgreSQL 17. O estado efetivamente observado dos checks remotos está no PR; este documento não infere sucesso remoto a partir do resultado local.

## Limites da evidência

Não houve deploy, merge, execução em produção, envio pelo provedor SMTP externo ou teste de navegador no futuro domínio Hostinger. HTTPS/certificado/origens/SMTP/chaves reais exigem configuração e homologação no ambiente. Não existem consumidores externos implantados; testes locais de contrato cumprem o reescopo V1.

Compilador ainda apresenta warnings preexistentes de operações unchecked em IdentityOpenApiConfiguration e uso de APIs deprecated em fixtures de testes. Não houve falha ou skip por esses warnings.
