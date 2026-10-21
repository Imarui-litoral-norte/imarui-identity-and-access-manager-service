# Evidência de validação — segunda rodada IIAMS-39 / PR #2

Execução final local: `2026-10-21T18:09:23-03:00`. Código validado e commitado em `526a38529d61d43dc7ebbca8aeea928e72a8d7c6`; o commit de documentação posterior não altera código/testes/migrations. O HEAD publicado e sua execução CI exata são registrados no [Draft PR #2](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/pull/2), evitando referências circulares de SHA no próprio documento.

```powershell
.\scripts\test-identity.ps1 -JavaHome 'C:\Users\lucas.l\.jdks\ms-17.0.18'
```

Maven executado: `-B clean verify`; Java 17.0.18; PostgreSQL **17.11** em container isolado, removido no finally. **191 testes, 0 falhas, 0 erros, 0 skips; BUILD SUCCESS** em 1m02s. JAR executável gerado; SHA-256 `fd572855dfca643e4ab8bba4be366381bd999925cd81af3b500696d58cecdf59`.

Flyway aplicou V1–V10 e Hibernate validou entidades. `MigrationUpgradeIntegrationTest` cobriu V5→V10 com Session/Refresh sem dono (histórico preservado e revogado) e V9→V10 com jobs esgotados e entregues de dez tentativas (contadores preservados, estado terminal e nova solicitação permitida). Histórico Flyway validado; V1–V9 sem alteração. Nenhum banco compartilhado ou de produção foi acessado; não se afirma que V6–V9 nunca foram aplicadas em ambiente compartilhado.

Resultado extraído dos XMLs Surefire: [test-results.json](test-results.json). Os 154 casos anteriores foram mantidos com adaptação dos cenários substituídos pelas novas decisões; 37 casos foram acrescentados. SMTP externo, domínio/TLS, cookies em navegador real e gestão de chaves do ambiente permanecem homologação.

| Suíte | Testes | Falhas/erros/skips |
|---|---:|---|
| br.com.imarui.ima.authentication.AuthenticationServiceApplicationTests | 1 | 0/0/0 |
| br.com.imarui.ima.authentication.core.AuthenticationDomainTest | 5 | 0/0/0 |
| br.com.imarui.ima.authentication.core.JwtKeyRotationTest | 2 | 0/0/0 |
| br.com.imarui.ima.authentication.core.SmtpChallengeMailTest | 1 | 0/0/0 |
| br.com.imarui.ima.authentication.feature.AuthenticationIntegrationTest | 27 | 0/0/0 |
| br.com.imarui.ima.authentication.feature.AuthenticationStabilizationIntegrationTest | 35 | 0/0/0 |
| br.com.imarui.ima.authentication.feature.MigrationUpgradeIntegrationTest | 2 | 0/0/0 |
| br.com.imarui.ima.identity.api.contract.identity.IdentityHttpContractTest | 3 | 0/0/0 |
| br.com.imarui.ima.identity.api.contract.identity.IdentitySelfDeleteHttpContractTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.api.contract.identityapplication.IdentityApplicationHttpContractTest | 9 | 0/0/0 |
| br.com.imarui.ima.identity.api.contract.openapi.IdentityOpenApiIntegrationTest | 21 | 0/0/0 |
| br.com.imarui.ima.identity.api.contract.openapi.IdentitySwaggerUiIntegrationTest | 2 | 0/0/0 |
| br.com.imarui.ima.identity.api.contract.tenant.TenantHttpContractTest | 2 | 0/0/0 |
| br.com.imarui.ima.identity.api.dto.identityapplication.response.IdentityApplicationResponseTest | 3 | 0/0/0 |
| br.com.imarui.ima.identity.core.application.identity.IdentityLifecycleUseCaseTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.core.application.identity.SelfDeleteIdentityUseCaseTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.core.application.identityapplication.ApproveIdentityApplicationUseCaseTest | 5 | 0/0/0 |
| br.com.imarui.ima.identity.core.domain.affiliation.AffiliationDomainTest | 7 | 0/0/0 |
| br.com.imarui.ima.identity.core.domain.affiliation.AffiliationValidityTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.core.domain.identity.IdentityDomainTest | 5 | 0/0/0 |
| br.com.imarui.ima.identity.core.domain.identity.IdentityLifecycleTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.core.domain.tenant.TenantDomainTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.feature.IdentityEndToEndTest | 1 | 0/0/0 |
| br.com.imarui.ima.identity.infra.persistence.affiliation.AffiliationHistoryIntegrationTest | 24 | 0/0/0 |
| br.com.imarui.ima.identity.infra.persistence.affiliation.AffiliationIntegrationTest | 7 | 0/0/0 |
| br.com.imarui.ima.identity.infra.persistence.identity.IdentityRevocationIntegrationTest | 3 | 0/0/0 |
| br.com.imarui.ima.identity.infra.persistence.identity.IdentitySelfDeleteIntegrationTest | 5 | 0/0/0 |
| br.com.imarui.ima.identity.infra.persistence.identityapplication.IdentityApplicationIntegrationTest | 12 | 0/0/0 |
| br.com.imarui.ima.platform.openapi.SwaggerOperationCustomizersTest | 3 | 0/0/0 |

## Escopo comprovado

- E-mail obrigatório e não único para três kinds/ROOT; nickname obrigatório no domínio/DTO/SQL; documentos e nickname por kind continuam únicos. Cadastro/approval compartilhando contato, atualização para contato existente e recovery de alvo inequívoco.
- SMTP: sucesso, falha→sucesso, cinco falhas, nenhuma sexta, FAILED permite novo job, backoff progressivo, estado durável/reinício, dois workers sem envio simultâneo mesmo com prazo vencido e ausência de token na fila/auditoria.
- Ativação/reset: links A/B coexistem apesar de envio ou falha, consumo em ordens diferentes, único vencedor concorrente para links distintos, finalidade isolada, expiração e rollback preservando segredo/credencial/estado.
- Convite administrativo: ator/alvo/resultado/origem UTC, permission negada, alvo inválido, ausência de justificativa e de segredos, eventos distintos e XFF/Forwarded falsos ignorados.
- Logout HTTP: Bearer/cookie iguais, proprietários ou SIDs diferentes, cookie inválido, access-only, refresh-only, repetição, Bearer expirado com repetição sem Bearer; falha real de escrita PostgreSQL no meio da revogação reverte a sessão.
- Regressão: três kinds PENDING→ACTIVE→login→JWT→refresh→logout/outbox, RS256/JWKS, issuer/audience, 900s e sete dias absolutos, ROOT/permissions, Identity/Tenant/Affiliation/deleção, CSRF/CORS, locks e replay.
- OpenAPI: parser estrito para todos os exemplos brutos, sem campos duplicados; schemas dos três kinds exigem e-mail e nickname.

## Execuções e checks

Além da execução final, foram usados `mvnw -B -DskipTests compile` e o script com `-Tests 'AuthenticationStabilizationIntegrationTest,IdentityApplicationIntegrationTest,AuthenticationIntegrationTest,IdentityOpenApiIntegrationTest'`. Uma regressão inicial de 154 casos apontou dois testes que ainda usavam a antiga unicidade de e-mail; um teste direcionado contava auditoria histórica sem filtrar ator. Os fixtures/assertions foram alinhados à regra atual; as execuções completas posteriores passaram. A revisão final também acrescentou cobertura para falha intermediária de logout e contadores históricos entregues acima de cinco.

`git diff --check` sem erros. CI usa o mesmo `clean verify` com PostgreSQL 17 e Java 17 em [.github/workflows/verify.yml](../../.github/workflows/verify.yml). [Execuções Verify IIAM](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/actions/workflows/verify.yml). Consultar o [PR #2](https://github.com/Imarui-litoral-norte/imarui-identity-and-access-manager-service/pull/2) para link da execução e SHA exatos conferidos após o push; sucesso local não substitui checks remotos.
