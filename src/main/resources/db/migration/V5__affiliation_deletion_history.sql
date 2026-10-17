-- Archive removed affiliations independently from operational entities and their uniqueness rules.
-- Previously deleted affiliation data cannot be reconstructed from Identity-only snapshots.
create table ima_affiliations__history (
 id uuid primary key,
 identity_history_id uuid not null,
 original_affiliation_id uuid not null,
 identity_id uuid not null,
 tenant_id uuid not null,
 type varchar(30) not null,
 previous_status varchar(20) not null,
 registration varchar(255), customer_code varchar(255), supplier_code varchar(255),
 started_at timestamptz not null, updated_at timestamptz not null, ended_at timestamptz,
 original_version bigint not null,
 deleted_at timestamptz not null, actor_id uuid not null, reason varchar(500)
);
-- These are lookup indexes only, not unique constraints or operational foreign keys.
create index idx_affiliation_history_original on ima_affiliations__history(original_affiliation_id, deleted_at);
create index idx_affiliation_history_identity on ima_affiliations__history(identity_id, deleted_at);
create index idx_affiliation_history_tenant on ima_affiliations__history(tenant_id, deleted_at);
create index idx_affiliation_history_operation on ima_affiliations__history(identity_history_id);
