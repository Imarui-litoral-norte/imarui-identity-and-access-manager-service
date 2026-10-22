-- Operational FK cascades must not erase the authorization audit trail with their Identity.
-- Correlate with the independent IdentityHistory snapshot already flushed by Identity deletion.
create function authorization_archive_removed_row() returns trigger language plpgsql as $$
declare subject uuid; resource_scope uuid; resource_kind varchar(80);
 historical_actor uuid; deletion_reason varchar(500); deletion_time timestamptz; history_id uuid;
begin
 if tg_table_name='authorization_identity_roles' then
  subject:=old.identity_id; resource_scope:=old.authorization_scope_id; resource_kind:='ROLE_ASSIGNMENT';
 elsif tg_table_name='authorization_denies' then
  subject:=old.identity_id; resource_scope:=old.scope_id; resource_kind:='DENY';
 else
  subject:=old.identity_id; resource_scope:=old.scope_id; resource_kind:='PERMISSION_DELEGATION';
 end if;
 select actor_id,reason,deleted_at,id into historical_actor,deletion_reason,deletion_time,history_id
  from ima_identities__history where original_identity_id=subject order by deleted_at desc limit 1;
 insert into authorization_audit(id,actor_identity_id,service_code,action,resource_type,resource_id,
  scope_id,occurred_at,reason,correlation_id,before_state)
 values(gen_random_uuid(),historical_actor,case when history_id is null then 'iiam-database' else 'iiam-identity' end,
  'AUTHORIZATION_ROW_REMOVED',resource_kind,old.id::text,resource_scope,coalesce(deletion_time,clock_timestamp()),
  coalesce(deletion_reason,'Operational authorization row removed'),coalesce(history_id,gen_random_uuid()),to_jsonb(old));
 return old;
end $$;
create trigger authorization_assignment_removed_audit before delete on authorization_identity_roles
 for each row execute function authorization_archive_removed_row();
create trigger authorization_deny_removed_audit before delete on authorization_denies
 for each row execute function authorization_archive_removed_row();
create trigger authorization_delegation_removed_audit before delete on authorization_permission_delegations
 for each row execute function authorization_archive_removed_row();
