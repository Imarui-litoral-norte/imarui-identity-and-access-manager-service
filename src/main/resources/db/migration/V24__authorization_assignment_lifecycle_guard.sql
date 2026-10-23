-- Role lifecycle and assignment creation serialize even for non-HTTP writers.
create function authorization_guard_assignment_lifecycle() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock(810082);
 if new.active and (tg_op='INSERT' or not old.active)
  and not exists(select 1 from authorization_roles where id=new.role_id and status='ACTIVE') then
  raise exception 'New assignments require an ACTIVE Role' using errcode='23514';
 end if;
 return new;
end $$;
create trigger authorization_00_assignment_lifecycle before insert or update on authorization_identity_roles
 for each row execute function authorization_guard_assignment_lifecycle();
create index ix_security_events_resource on authorization_security_events(service_code,resource_id,occurred_at);
