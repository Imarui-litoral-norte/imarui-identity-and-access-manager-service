-- One-time bootstrap authority for existing ROOT assignments, restricted to their current scopes
-- and to already granted internal permissions. Registering an external catalog never creates this authority.
insert into authorization_permission_delegations(id,identity_id,permission_code,scope_id,redelegable,active,reason,created_at,updated_at)
 select gen_random_uuid(),a.identity_id,p.code,a.authorization_scope_id,true,true,
 'IIAMS-81 migration of protected ROOT governance',current_timestamp,current_timestamp
 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
 join authorization_role_permissions rp on rp.role_id=r.id join authorization_permissions p on p.id=rp.permission_id
 where a.active and r.active and r.name='ROOT' and p.service_code='iiam' and not p.deprecated
 on conflict do nothing;

insert into authorization_audit(id,service_code,action,resource_type,resource_id,scope_id,occurred_at,reason,correlation_id,after_state)
 select gen_random_uuid(),'iiam-migration','ROOT_DELEGATION_MIGRATED','PERMISSION_DELEGATION',d.id::text,
 d.scope_id,d.created_at,d.reason,gen_random_uuid(),jsonb_build_object('identityId',d.identity_id,
 'permission',d.permission_code,'scopeId',d.scope_id,'redelegable',d.redelegable)
 from authorization_permission_delegations d where d.reason='IIAMS-81 migration of protected ROOT governance';

create function authorization_guard_root_identity() returns trigger language plpgsql as $$
declare affected_scope uuid;
begin
 perform pg_advisory_xact_lock(810082);
 if tg_op='UPDATE' and new.status<>'DISABLED' then return new; end if;
 for affected_scope in select a.authorization_scope_id from authorization_identity_roles a
  join authorization_roles r on r.id=a.role_id where a.identity_id=old.id and a.active and r.active and r.name='ROOT'
 loop
  if not exists(select 1 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
   join ima_identities i on i.id=a.identity_id where a.authorization_scope_id=affected_scope
   and a.active and r.active and r.name='ROOT' and i.id<>old.id and i.status in ('ACTIVE','PENDING')) then
   raise exception 'Last recovery administrator is protected' using errcode='23514';
  end if;
 end loop;
 if tg_op='DELETE' then return old; end if;
 return new;
end $$;
create trigger authorization_root_identity_guard before update of status or delete on ima_identities
 for each row execute function authorization_guard_root_identity();

create function authorization_guard_assignment() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 if tg_op='UPDATE' and (new.identity_id<>old.identity_id or new.role_id<>old.role_id
  or new.authorization_scope_id<>old.authorization_scope_id or new.id<>old.id) then
  raise exception 'Assignment identity and resource are immutable' using errcode='23514';
 end if;
 if tg_op='UPDATE' and new.active then return new; end if;
 if exists(select 1 from authorization_roles where id=old.role_id and name='ROOT')
  and not exists(select 1 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
   join ima_identities i on i.id=a.identity_id where a.id<>old.id and a.authorization_scope_id=old.authorization_scope_id
   and a.active and r.active and r.name='ROOT' and i.status in ('ACTIVE','PENDING')) then
  raise exception 'Last recovery assignment is protected' using errcode='23514';
 end if;
 if tg_op='DELETE' then return old; end if;
 return new;
end $$;
create trigger authorization_assignment_guard before update or delete on authorization_identity_roles
 for each row execute function authorization_guard_assignment();

create function authorization_guard_root_definition() returns trigger language plpgsql as $$
declare root_role bigint;
begin
 perform pg_advisory_xact_lock(810082);
 if tg_table_name='authorization_roles' then root_role:=old.id; else root_role:=old.role_id; end if;
 if exists(select 1 from authorization_roles where id=root_role and name='ROOT') then
  raise exception 'ROOT definition is protected' using errcode='23514';
 end if;
 return old;
end $$;
create trigger authorization_root_role_delete_guard before delete on authorization_roles
 for each row execute function authorization_guard_root_definition();
create trigger authorization_root_permission_delete_guard before update or delete on authorization_role_permissions
 for each row execute function authorization_guard_root_definition();

create function authorization_lock_tenant_lifecycle() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 return new;
end $$;
create trigger authorization_tenant_lifecycle_guard before update of status on ima_tenants
 for each row execute function authorization_lock_tenant_lifecycle();
