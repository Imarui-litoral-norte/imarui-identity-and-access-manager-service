create table authorization_denies (
 id uuid primary key, permission_code varchar(100) not null references authorization_permissions(code),
 scope_id uuid not null references authorization_scopes(id), identity_id uuid references ima_identities(id) on delete cascade,
 role_id bigint references authorization_roles(id), active boolean not null default true,
 reason varchar(500) not null check(length(trim(reason))>0), created_at timestamptz not null,
 updated_at timestamptz not null, version bigint not null default 0 check(version>=0),
 check((identity_id is null)<>(role_id is null)), check(updated_at>=created_at)
);
create index ix_deny_identity_scope on authorization_denies(identity_id,scope_id,permission_code) where active;
create index ix_deny_role_scope on authorization_denies(role_id,scope_id,permission_code) where active;
create table authorization_permission_delegations (
 id uuid primary key, identity_id uuid not null references ima_identities(id) on delete cascade,
 permission_code varchar(100) not null references authorization_permissions(code), scope_id uuid not null references authorization_scopes(id),
 redelegable boolean not null default false, active boolean not null default true,
 reason varchar(500) not null check(length(trim(reason))>0), created_at timestamptz not null,
 updated_at timestamptz not null, version bigint not null default 0 check(version>=0),
 check(updated_at>=created_at)
);
create unique index uk_active_permission_delegation on authorization_permission_delegations(identity_id,permission_code,scope_id) where active;
create index ix_permission_delegation_recipient on authorization_permission_delegations(identity_id,scope_id) where active;
