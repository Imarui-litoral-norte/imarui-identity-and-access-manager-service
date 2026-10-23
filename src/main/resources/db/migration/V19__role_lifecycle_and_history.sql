select pg_advisory_xact_lock(810082);
alter table authorization_roles add column status varchar(10);
update authorization_roles set status=case when active then 'ACTIVE' else 'INACTIVE' end;
alter table authorization_roles alter column status set not null;
alter table authorization_roles alter column status set default 'ACTIVE';
alter table authorization_roles add constraint ck_role_lifecycle check(status in ('ACTIVE','INACTIVE','ARCHIVED'));
-- Retain the physical boolean as a compatibility projection for historical guards/clients.
create function authorization_sync_role_lifecycle() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 if tg_op='UPDATE' and old.status='ARCHIVED' and new.status<>'ARCHIVED' then
  raise exception 'Archived Role cannot be reactivated' using errcode='23514';
 end if;
 if tg_op='UPDATE' and new.status=old.status and new.active<>old.active then
  if old.status='ARCHIVED' then raise exception 'Archived Role is immutable' using errcode='23514'; end if;
  new.status:=case when new.active then 'ACTIVE' else 'INACTIVE' end;
 elsif tg_op='INSERT' and new.status='ACTIVE' and not new.active then new.status:='INACTIVE';
 end if;
 new.active:=(new.status='ACTIVE');
 if new.name='ROOT' and new.status<>'ACTIVE' then raise exception 'ROOT lifecycle is protected' using errcode='23514'; end if;
 if new.status='ARCHIVED' and exists(select 1 from authorization_identity_roles where role_id=new.id and active) then
  raise exception 'Revoke active assignments before archiving Role' using errcode='23514';
 end if;
 return new;
end $$;
create trigger authorization_00_role_lifecycle before insert or update on authorization_roles
 for each row execute function authorization_sync_role_lifecycle();
create table authorization_roles__history (
 id uuid primary key, original_role_id bigint not null, deleted_at timestamptz not null,
 actor_identity_id uuid, reason varchar(500) not null, correlation_id uuid not null,
 role_state jsonb not null, permission_state jsonb not null, deny_state jsonb not null
);
create index ix_role_history_original on authorization_roles__history(original_role_id,deleted_at);
create function authorization_guard_role_delete() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 if old.status<>'ARCHIVED' or exists(select 1 from authorization_identity_roles where role_id=old.id) then
  raise exception 'Only archived, unassigned Roles may be deleted' using errcode='23514';
 end if;
 if not exists(select 1 from authorization_roles__history where original_role_id=old.id and role_state->>'version'=old.version::text) then
  raise exception 'Role deletion requires immutable history' using errcode='23514';
 end if;
 return old;
end $$;
create trigger authorization_role_deletion_history before delete on authorization_roles
 for each row execute function authorization_guard_role_delete();
