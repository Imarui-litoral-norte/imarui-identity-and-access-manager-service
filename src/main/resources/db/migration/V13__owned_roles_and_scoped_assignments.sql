alter table authorization_roles drop constraint authorization_roles_name_key;
alter table authorization_roles add column ownership varchar(10) not null default 'GLOBAL';
alter table authorization_roles add column owner_scope_id uuid references authorization_scopes(id);
alter table authorization_roles add column owner_tenant_id uuid;
alter table authorization_roles add column version bigint not null default 0 check(version>=0);
alter table authorization_roles add column updated_at timestamptz not null default current_timestamp;
update authorization_roles set updated_at=created_at;
alter table authorization_tenant_scopes add constraint uk_tenant_scope_pair unique(tenant_id,scope_id);
alter table authorization_roles add constraint fk_role_tenant_scope foreign key(owner_tenant_id,owner_scope_id)
 references authorization_tenant_scopes(tenant_id,scope_id);
alter table authorization_roles add constraint ck_role_owner check(
 (ownership='GLOBAL' and owner_scope_id is null and owner_tenant_id is null) or
 (ownership='SCOPE' and owner_scope_id is not null and owner_tenant_id is null) or
 (ownership='TENANT' and owner_scope_id is not null and owner_tenant_id is not null));
alter table authorization_roles add constraint ck_role_name check(length(trim(name))>0 and
 (lower(name)<>'root' or (name='ROOT' and ownership='GLOBAL')));
-- Preserve the previous case-sensitive name semantics while moving uniqueness to the owner.
create unique index uk_global_role_name on authorization_roles(name) where ownership='GLOBAL';
create unique index uk_scope_role_name on authorization_roles(owner_scope_id,name) where ownership='SCOPE';
create unique index uk_tenant_role_name on authorization_roles(owner_tenant_id,name) where ownership='TENANT';

-- Every prior composite Identity/Role key and created_at survives as one explicit PLATFORM assignment.
alter table authorization_identity_roles add column id uuid not null default gen_random_uuid();
alter table authorization_identity_roles add column authorization_scope_id uuid not null
 default '00000000-0000-0000-0000-000000000001' references authorization_scopes(id);
alter table authorization_identity_roles add column active boolean not null default true;
alter table authorization_identity_roles add column updated_at timestamptz not null default current_timestamp;
update authorization_identity_roles set updated_at=created_at;
alter table authorization_identity_roles add column version bigint not null default 0 check(version>=0);
alter table authorization_identity_roles drop constraint authorization_identity_roles_pkey;
alter table authorization_identity_roles drop column scope;
alter table authorization_identity_roles add primary key(id);
alter table authorization_identity_roles add constraint uk_scoped_role_assignment unique(identity_id,role_id,authorization_scope_id);
create index ix_assignment_identity_scope on authorization_identity_roles(identity_id,authorization_scope_id) where active;
create index ix_assignment_role_scope on authorization_identity_roles(role_id,authorization_scope_id) where active;

create function authorization_guard_role_owner() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 if new.ownership<>old.ownership or new.owner_scope_id is distinct from old.owner_scope_id
  or new.owner_tenant_id is distinct from old.owner_tenant_id then
  raise exception 'Role ownership is immutable' using errcode='23514';
 end if;
 if old.name='ROOT' and (new.name<>old.name or not new.active) then
  raise exception 'ROOT Role is protected' using errcode='23514';
 end if;
 return new;
end $$;
create trigger authorization_role_owner_guard before update on authorization_roles
 for each row execute function authorization_guard_role_owner();
