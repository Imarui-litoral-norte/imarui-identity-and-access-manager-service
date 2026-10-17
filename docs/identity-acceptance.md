# IIAMS-8 Identity acceptance

Verified on 2026-10-17 against the completed working branch `feat/identity-completion`,
based on main `22da35f5bc2488e316fbb98d796369ee3bf4656b`.

## Executed acceptance

`scripts/test-identity.ps1 -JavaHome <Java 17>` created a fresh disposable PostgreSQL 17.11,
applied Flyway V1–V4, initialized the complete Spring application with Hibernate schema validation,
ran HTTP scenarios on a real server and built the executable jar through `clean verify`.
Result: **94 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
The script removed its own test container afterwards.

| Suite | Tests |
| --- | ---: |
| authentication.AuthenticationServiceApplicationTests | 1 |
| identity.api.contract.identity.IdentityHttpContractTest | 3 |
| identity.api.contract.identity.IdentitySelfDeleteHttpContractTest | 1 |
| identity.api.contract.identityapplication.IdentityApplicationHttpContractTest | 9 |
| identity.api.contract.openapi.IdentityOpenApiIntegrationTest | 20 |
| identity.api.contract.openapi.IdentitySwaggerUiIntegrationTest | 2 |
| identity.api.contract.tenant.TenantHttpContractTest | 2 |
| identity.api.dto.identityapplication.response.IdentityApplicationResponseTest | 3 |
| identity.core.application.identity.IdentityLifecycleUseCaseTest | 1 |
| identity.core.application.identity.SelfDeleteIdentityUseCaseTest | 1 |
| identity.core.application.identityapplication.ApproveApproveIdentityApplicationUseCaseTest | 5 |
| identity.core.domain.affiliation.AffiliationDomainTest | 7 |
| identity.core.domain.affiliation.AffiliationValidityTest | 1 |
| identity.core.domain.identity.IdentityDomainTest | 5 |
| identity.core.domain.identity.IdentityLifecycleTest | 1 |
| identity.core.domain.tenant.TenantDomainTest | 1 |
| identity.feature.IdentityEndToEndTest | 1 |
| identity.infra.persistence.affiliation.AffiliationIntegrationTest | 7 |
| identity.infra.persistence.identity.IdentityRevocationIntegrationTest | 3 |
| identity.infra.persistence.identity.IdentitySelfDeleteIntegrationTest | 5 |
| identity.infra.persistence.identityapplication.IdentityApplicationIntegrationTest | 12 |
| platform.openapi.SwaggerOperationCustomizersTest | 3 |
| Total | 94 |

## Requirement evidence

| Jira task | Implemented behavior | Executed evidence |
| --- | --- | --- |
| IIAMS-16 | Locked activate/disable/reactivate use cases persist IdentityResult | IdentityLifecycleTest; IdentityDomainTest for all kinds; HTTP lifecycle |
| IIAMS-17 | Owner-derived self-delete, optional reason, HTTP 204, revocation | selfDeleteTakesItsTargetFromTrustedActorAndRejectsInvalidReasonBeforeWriting; httpOnboardingLifecycleErrorsAndSelfDeleteAreFunctional |
| IIAMS-18 | Full typed snapshot and JOINED hard delete in one transaction | selfDeleteCommitsFullSnapshotRemovesJoinedRowsAndPermitsReregistration for all kinds; failingHistoryInsertRollsBackDeletionAndRevocationRequest; failingOperationalDeleteRollsBackAlreadyWrittenHistory |
| IIAMS-19 | Approval creates correct Identity kind and selected Affiliation atomically | approvalPersistsCorrectKindAndReviewerAndCannotResolveTwice; approvalSupportsEachAffiliationSpecialization; scopedCodesAreUniqueOnlyWithinTenantAndApprovalRollsBack |
| IIAMS-20 | Rejection persists actor, time and reason with no provisioning | rejectionIsAuditedAndDoesNotProvisionIdentity |
| IIAMS-21 | CurrentActor port and trusted security adapter | actorFailureStopsApprovalBeforeAnyWrite; missingActorCannotResolveOrDelete |
| IIAMS-22 | CPF/CNPJ and per-kind normalized e-mail uniqueness in create/approve | duplicatesAreBlockedBothBeforeAndAfterApplication; sameEmailIsAllowedAcrossKindsAndRejectionReleasesPendingKey; concurrentDuplicateSubmissionsHaveOneWinner; concurrentApprovalCanResolveOnlyOnce |
| IIAMS-23 | Tenant PENDING -> ACTIVE <-> DISABLED with preserved record/code | tenantLifecyclePreservesCodeAndOriginalActivation; disabledTenantBlocksApprovalWithoutPartialIdentity |
| IIAMS-24 | Single effective-affiliation policy applied to operational queries | tenantContextNeverMutatesIntrinsicAffiliationState; lifecycleQueriesHonorTenantAndIdentityWithoutMutatingAffiliation |
| IIAMS-25 | All five specializations, lifecycle timestamps and scoped identifiers | everyAffiliationSpecializationPreservesLifetimeAndRejectsInvalidTransitions; approvalSupportsEachAffiliationSpecialization; scopedCodesAreUniqueOnlyWithinTenantAndApprovalRollsBack |
| IIAMS-26 | AccessRevocation port and durable outbox contract | Persisted IDENTITY/TENANT requests in lifecycle tests; documented consumer contract in the module integration guide |
| IIAMS-27 | Disable/delete emit transactional requests; reactivation cannot restore prior tokens | failingRevocationRollsBackIdentityDisable; oldIdentityTokenRemainsRevokedAfterReactivation; scopedTokenRemainsRevokedAfterTenantReactivation |
| IIAMS-28 | Domain/validation/conflict/not-found/access ProblemDetail responses | httpValidationConflictAndAdministrativePermissionAreEnforced; unknownTenantDuringApprovalReturnsNotFoundAndNoPartialWrites; HTTP lifecycle errors |
| IIAMS-29 | Identity kind plus typed Person/LegalEntity/Service details | httpResponseDiscriminatesKindsAndProtectsOtherIdentities for all kinds |
| IIAMS-30 | Functional controllers, creation Location, explicit success/error schemas and Bearer documentation | documentedOpenApiContainsActualResponsesAndSecurity; complete HTTP onboarding/lifecycle/delete |
| IIAMS-31 | Removed unused registration DTO and seven User exceptions | Repository source search: no User artifacts or references remain in Identity; clean compile |
| IIAMS-32 | Core ports separate Identity from Spring Security and Authentication internals | Source search: no security/authentication imports in identity/core; full clean verify |
| IIAMS-33 | Lifecycle, time, natural-key and subtype invariants | IdentityDomainTest and IdentityLifecycleTest |
| IIAMS-34 | Application orchestration tested with mocked ports and real transactions | ApproveIdentityApplicationUseCaseTest; IdentityApplicationIntegrationTest |
| IIAMS-35 | Real migrations, polymorphic persistence, snapshot, deletion and rollback | Flyway V1–V4 on fresh PostgreSQL; IdentityApplicationIntegrationTest, AffiliationIntegrationTest and IdentitySelfDeleteIntegrationTest |
| IIAMS-36 | Actual HTTP contracts, variants, status, authorization and ProblemDetail | IdentityHttpContractTest, IdentityApplicationHttpContractTest, TenantHttpContractTest, IdentitySelfDeleteHttpContractTest and IdentityOpenApiIntegrationTest |
| IIAMS-37 | Complete application bootstrap, no placeholders/legacy, fresh-schema clean verify and jar | 94 passing tests; source gate; executable jar built |

Stories IIAMS-9–IIAMS-15 and Epic IIAMS-8 consolidate the completed requirements above.
Source gate found no UnsupportedOperationException/TODO/FIXME, legacy User references,
or concrete Authentication/Spring Security dependency in Identity core.
Tenant model/controllers contain no DELETE endpoint or DELETED status.

## Operational prerequisites and integration boundary

Deployment requires PostgreSQL credentials, a strong JWT_SECRET, the trusted initial operator Identity/token,
and the deliberate baseline procedure for an existing pre-Flyway database, as documented in the database and operations guides.
Fresh schema and the full API flow have been validated in an isolated test environment.
Authentication's future session/refresh outbox consumer and login/token issuance remain its own module scope.
Identity already persists revocation requests atomically and enforces their cutoff for resource API access.
No production database migration or service restart was performed during acceptance.

## Documentation and feature test completion

The documentation follows the organization reviewed in Jeep Club's `develop` branch, adapted to this repository's actual use cases and ports:

- [Global index](README.md), [architecture](architecture/README.md), [module organization](architecture/module-organization.md) and [feature rules](architecture/feature-development-rules.md).
- [Identity module](../src/main/java/br/com/imarui/ima/identity/docs/README.md): business rules, transactional flows, integration boundaries and feature coverage.
- [Platform OpenAPI](../src/main/java/br/com/imarui/ima/platform/openapi/docs/README.md): annotations, customizers and documentation ownership.
- Customized Swagger at `/docs`, with module tags and ordered feature groups, examples for the three Identity kinds and five affiliation choices, permission/ownership metadata, conditional fields and per-operation errors for all 17 Identity operations.
- [Executable test strategy](testing/README.md), organized into domain, application, PostgreSQL persistence, HTTP contracts, OpenAPI and composed feature scenarios. Existing scenarios were preserved and expanded from 58 to 94 test executions.

The final `clean verify` ran after the last Java/test change. Swagger JavaScript passed `node --check`.
All local Markdown links were checked. Browser review confirmed feature navigation, approval request/response/error examples,
permission display and tag filtering without browser warning/error logs. No HTTP mutation was executed through Swagger during this review.
