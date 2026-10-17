# Identity module

## Lifecycle and onboarding

Identity and Tenant use `PENDING -> ACTIVE <-> DISABLED`. First activation timestamps are preserved.
Affiliation uses `ACTIVE <-> INACTIVE`; deactivation sets `endedAt`, reactivation clears it and preserves `startedAt`.
Identity timestamps are UTC with microsecond precision, matching PostgreSQL.

Public `POST /api/v1/identity-applications` creates a request with `type` PERSON, LEGAL_ENTITY or SERVICE.
Pending requests are unique by CPF/CNPJ where applicable and by normalized e-mail **within IdentityKind**.
An e-mail can be used by different kinds. Existing operational identities reserve these keys in all statuses.
Rejected requests release pending keys. Historical snapshots do not reserve natural keys.

Approval revalidates current conflicts and requires an ACTIVE Tenant and a trusted active reviewer.
It creates a PENDING Identity and an ACTIVE affiliation, then records the reviewer, review timestamp and
actual resulting IDs. Explicit Identity activation makes the relationship operational. All writes are atomic.
Rejection records the reviewer and reason without creating Identity or Affiliation. Resolved requests cannot be resolved again.

Approval request example:

```json
{
  "tenantId": "<tenant UUID>",
  "affiliation": {"type": "EMPLOYEE", "registration": "EMP-100"}
}
```

Supported types: EMPLOYEE requires `registration`; CUSTOMER requires `customerCode`;
SUPPLIER requires `supplierCode`; PARTNER and EXTERNAL require no specific identifier.
Fields belonging to another type are rejected. Specific codes are unique within Tenant, including inactive affiliations.
Tenant row locks serialize approvals; PostgreSQL indexes and composite foreign keys enforce code integrity.
Registration/review acquire ordered transaction-scoped advisory locks on hashed natural keys, recheck rules,
and retain unique indexes as the final protection against concurrent duplicates.

## Effective affiliation

`AffiliationValidity.isEffective` requires matching IDs and ACTIVE Identity, Affiliation and Tenant.
Repository operational lists (`findAllActive`, `findByTenantId`, `findByIdentityId`) and active-existence checks apply this policy.
`findById`/`findByIdForUpdate` are administrative reads and include inactive records for lifecycle operations.
Identity/Tenant generic ID reads include pending/disabled records; operational consumers must explicitly check state.
Disabling Tenant never cascades state changes into Identity/Affiliation. Reactivation never restores an explicitly inactive affiliation.

## Self-delete and history

`DELETE /api/v1/identities/me` resolves the target exclusively from the authenticated subject.
An optional body contains `reason` (1–500 nonblank characters). Success returns 204.
One transaction records the revocation request, inserts a snapshot in `ima_identities__history`,
deletes all operational affiliations/specializations and deletes the Identity hierarchy.
Failure of history, deletion or durable revocation rolls back the whole operation.
The snapshot contains the original UUID/kind/status, lifecycle timestamps, deletion time, actor, reason,
and JSON of every persisted common/specialized Identity field (including email/phone verification and photo metadata).
The history table has no foreign keys to the deleted Identity and no unique CPF/CNPJ/e-mail constraints.
Application audit references remain intact after deletion. There is no DELETED state or Tenant delete endpoint.

## Trusted actor and resource API access

`CurrentActor` is a core application port. Its infrastructure adapter requires an authenticated UUID subject
matching a persisted ACTIVE Identity. Core has no Spring Security imports.
API access accepts HS256 Bearer JWTs signed using `JWT_SECRET`, with issuer `JWT_ISSUER`, UUID `sub`, `iat`,
expiration, and a `permissions` array. Optional `tenant_id` requires an effective affiliation in that Tenant.
The initial operator Identity and its trusted token must be provisioned by the deployment/Authentication operator;
the service does not create a default administrator or issue login tokens.

| Surface | Access |
| --- | --- |
| POST identity-applications | Public creation |
| GET identity-applications/{id} | IDENTITY_APPLICATION_READ |
| /admin/identity-applications | IDENTITY_APPLICATION_REVIEW |
| /admin/identities | IDENTITY_MANAGE |
| GET identities/{id} | Own Identity or IDENTITY_READ |
| DELETE identities/me | Authenticated active owner |
| /admin/tenants | TENANT_MANAGE |
| GET tenants/{id} | TENANT_READ |

Responses use an outer `kind` and typed `details` for Identity; IdentityApplication uses `type` and typed `details`.
Errors use RFC 9457 `application/problem+json`, HTTP status, `code` and timestamp.
Validation = 400; missing actor = 401; insufficient access = 403; not found = 404;
natural-key conflict/invalid lifecycle/concurrent write = 409. Personal values are omitted from conflict messages.
OpenAPI describes actual requests, variants, responses and Bearer access.

## Identity to Authentication contract

`AccessRevocation.revokeIdentity(identityId)` is invoked for disable and self-delete;
`revokeTenant(tenantId)` is invoked for Tenant disable. Each call must persist in the current transaction or fail it.
`TransactionalAccessRevocation` writes a durable outbox in `ima_access_revocation_requests` with request UUID,
scope, subject UUID, requested time and nullable processed time. Repeated requests are safe to consume idempotently.
Authentication must terminate matching sessions, revoke refresh tokens/credentials as appropriate,
and set `processed_at` only after its own invalidation succeeds, retrying failed requests.
It must retain the request time for token cutoff checks; processed requests remain part of the audit trail.

The resource API already rejects a disabled/deleted Identity, invalid Tenant context and JWTs whose `iat`
precedes a relevant durable revocation request, including after reactivation.
Reactivation emits no restoration command. Because JWT iat is represented in seconds, tokens issued during
the same second as a revocation are conservatively rejected; fresh issuance must occur after that cutoff.
Future Authentication must apply equivalent rules to session/refresh issuance and consumption.
Identity core imports no Session/RefreshToken/JWT model. The outbox consumer and login/token issuance belong
to the next Authentication work; durable requests and the resource access checks are functional here.

## Database rollout

Fresh PostgreSQL schema: Flyway V1 creates the existing mapped tables; V2 adds history/outbox;
V3 adds per-kind email/pending natural-key indexes; V4 adds Tenant-scoped affiliation code constraints.
Hibernate uses `validate`. Do not enable automatic baseline or use schema `update` to bypass migrations.

For a populated pre-Flyway schema, back up and compare it to V1 before explicitly baselining at version 1.
The old application email column must be widened to varchar(254), and all V1 columns/types/constraints
must be reconciled before that baseline is accepted. V3/V4 fail on conflicting existing data rather than
delete or deduplicate it. Reconcile such data explicitly before rollout. No production database is modified by the test script.

## Legacy inventory

Removed unused UserRegistrationRequestDTO and seven unused User exceptions (CPF invalid/not found,
disabled, ID not found, invalid credentials/password, password change not required).
Repository search found no consumers, so no authentication behavior was moved or replaced.
Identity owns no password or credential logic; the existing authentication security utilities remain in their module.
