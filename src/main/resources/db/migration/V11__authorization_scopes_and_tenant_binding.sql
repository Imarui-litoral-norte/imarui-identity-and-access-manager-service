-- PLATFORM becomes a named administrative root, not a geography or a wildcard for future roots.
create table authorization_scopes (
 id uuid primary key, code varchar(80) not null unique, name varchar(150) not null,
 status varchar(20) not null check(status in ('ACTIVE','DISABLED')),
 parent_id uuid references authorization_scopes(id), created_at timestamptz not null,
 updated_at timestamptz not null, version bigint not null default 0 check(version>=0),
 check(id is distinct from parent_id), check(updated_at>=created_at),
 check(code ~ '^[A-Z][A-Z0-9_]{0,79}$'), check(length(trim(name))>0)
);
create index ix_authorization_scopes_parent on authorization_scopes(parent_id);
insert into authorization_scopes(id,code,name,status,created_at,updated_at)
 values('00000000-0000-0000-0000-000000000001','IIAM_PLATFORM','IIAM administrative boundary','ACTIVE',current_timestamp,current_timestamp);

create table authorization_tenant_scopes (
 tenant_id uuid primary key references ima_tenants(id),
 scope_id uuid not null unique references authorization_scopes(id)
);
-- Stable binding; no Tenant UUID or operational record is replaced.
insert into authorization_scopes(id,code,name,status,parent_id,created_at,updated_at)
 select gen_random_uuid(),'TENANT_'||upper(replace(id::text,'-','')),name,'ACTIVE',
 '00000000-0000-0000-0000-000000000001',created_at,updated_at from ima_tenants;
insert into authorization_tenant_scopes(tenant_id,scope_id)
 select t.id,s.id from ima_tenants t join authorization_scopes s on s.code='TENANT_'||upper(replace(t.id::text,'-',''));

create table authorization_audit (
 id uuid primary key, actor_identity_id uuid, service_code varchar(100),
 action varchar(100) not null, resource_type varchar(80) not null, resource_id varchar(100) not null,
 scope_id uuid, occurred_at timestamptz not null, reason varchar(500), correlation_id uuid not null,
 before_state jsonb, after_state jsonb
);
create index ix_authorization_audit_resource on authorization_audit(resource_type,resource_id,occurred_at);
create index ix_authorization_audit_scope on authorization_audit(scope_id,occurred_at);
-- Serialize hierarchy writes across instances, including writes outside a Java persistence context.
create function authorization_guard_scope() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 if tg_op='DELETE' then
  if old.id='00000000-0000-0000-0000-000000000001' then
   raise exception 'Administrative boundary is protected' using errcode='23514';
  end if;
  return old;
 end if;
 if tg_op='UPDATE' and (new.id<>old.id or new.code<>old.code) then
  raise exception 'Scope identity is immutable' using errcode='23514';
 end if;
 if new.id='00000000-0000-0000-0000-000000000001' and (new.parent_id is not null or new.status<>'ACTIVE') then
  raise exception 'Administrative boundary is protected' using errcode='23514';
 end if;
 if new.parent_id is not null and exists(
  with recursive lineage as (
   select id,parent_id from authorization_scopes where id=new.parent_id
   union select s.id,s.parent_id from authorization_scopes s join lineage l on s.id=l.parent_id
  ) select 1 from lineage where id=new.id
 ) then raise exception 'Scope cycle' using errcode='23514'; end if;
 return new;
end $$;
create trigger authorization_scope_guard before insert or update or delete on authorization_scopes
 for each row execute function authorization_guard_scope();

create function authorization_create_tenant_scope() returns trigger language plpgsql as $$
declare new_scope uuid := gen_random_uuid();
begin
 insert into authorization_scopes(id,code,name,status,parent_id,created_at,updated_at)
 values(new_scope,'TENANT_'||upper(replace(new.id::text,'-','')),new.name,'ACTIVE',
  '00000000-0000-0000-0000-000000000001',new.created_at,new.updated_at);
 insert into authorization_tenant_scopes(tenant_id,scope_id) values(new.id,new_scope);
 insert into authorization_audit(id,service_code,action,resource_type,resource_id,scope_id,occurred_at,correlation_id,after_state)
 values(gen_random_uuid(),'iiam-identity','TENANT_SCOPE_CREATED','TENANT',new.id::text,new_scope,
  new.created_at,gen_random_uuid(),jsonb_build_object('tenantId',new.id,'scopeId',new_scope));
 return new;
end $$;
create trigger authorization_tenant_created after insert on ima_tenants
 for each row execute function authorization_create_tenant_scope();

create function authorization_guard_tenant_binding() returns trigger language plpgsql as $$
begin
 raise exception 'Tenant Scope binding is stable and cannot be replaced or removed' using errcode='23514';
end $$;
create trigger authorization_tenant_binding_guard before update or delete on authorization_tenant_scopes
 for each row execute function authorization_guard_tenant_binding();
