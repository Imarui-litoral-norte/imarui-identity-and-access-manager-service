-- V21 guarded DISABLED/deletion, but an ACTIVE -> PENDING write could remove
-- the last activated recovery operator. Preserve all prior history and IDs.
select pg_advisory_xact_lock(810082);
create or replace function authorization_guard_root_identity() returns trigger language plpgsql as $$
declare affected_scope uuid;
begin
 perform pg_advisory_xact_lock(810082);
 if tg_op='UPDATE' and new.status='ACTIVE' then return new; end if;
 for affected_scope in select a.authorization_scope_id from authorization_identity_roles a
  join authorization_roles r on r.id=a.role_id where a.identity_id=old.id and a.active and r.active and r.name='ROOT'
 loop
  if not exists(select 1 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
   join ima_identities i on i.id=a.identity_id where a.authorization_scope_id=affected_scope
   and a.active and r.active and r.name='ROOT' and i.id<>old.id
   and (i.status='ACTIVE' or old.status<>'ACTIVE' and i.status='PENDING')) then
   raise exception 'Last recovery administrator is protected' using errcode='23514';
  end if;
 end loop;
 if tg_op='DELETE' then return old; end if;
 return new;
end $$;
