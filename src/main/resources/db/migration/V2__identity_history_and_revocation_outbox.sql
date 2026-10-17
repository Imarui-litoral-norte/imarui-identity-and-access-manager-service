create table ima_identities__history (
 id uuid primary key, original_identity_id uuid not null, kind varchar(30) not null,
 previous_status varchar(20) not null, created_at timestamptz not null, updated_at timestamptz not null,
 activated_at timestamptz, disabled_at timestamptz, deleted_at timestamptz not null,
 actor_id uuid not null, reason varchar(500), snapshot text not null
);
create index idx_identity_history_original on ima_identities__history(original_identity_id, deleted_at);
create table ima_access_revocation_requests (
 id uuid primary key, scope varchar(20) not null check (scope in ('IDENTITY', 'TENANT')),
 subject_id uuid not null, requested_at timestamptz not null, processed_at timestamptz
);
create index idx_revocation_pending on ima_access_revocation_requests(requested_at) where processed_at is null;
