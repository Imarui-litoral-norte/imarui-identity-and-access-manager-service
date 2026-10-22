create table authorization_root_transfers (
 id uuid primary key, initiator_id uuid not null, initiator_session_id uuid not null, recipient_id uuid not null,
 scope_id uuid not null references authorization_scopes(id),
 reason varchar(500) not null, correlation_id uuid not null, created_at timestamptz not null,
 expires_at timestamptz not null, confirmed_at timestamptz, check(initiator_id<>recipient_id),
 check(expires_at>created_at), unique(initiator_id,correlation_id)
);
create index ix_root_transfer_pending on authorization_root_transfers(recipient_id,expires_at) where confirmed_at is null;
create table authorization_scopes__history (
 id uuid primary key, original_scope_id uuid not null, deleted_at timestamptz not null,
 actor_identity_id uuid, reason varchar(500) not null, correlation_id uuid not null, scope_state jsonb not null
);
create index ix_scope_history_original on authorization_scopes__history(original_scope_id,deleted_at);
create function authorization_scope_deletion_history() returns trigger language plpgsql as $$
begin
 if old.status<>'DISABLED' or not exists(select 1 from authorization_scopes__history
  where original_scope_id=old.id and scope_state->>'version'=old.version::text) then
  raise exception 'Scope deletion requires disabled state and immutable history' using errcode='23514';
 end if;
 return old;
end $$;
create trigger authorization_scope_delete_history before delete on authorization_scopes
 for each row execute function authorization_scope_deletion_history();
create function authorization_immutable_history() returns trigger language plpgsql as $$
begin raise exception 'Security history is append-only' using errcode='23514'; end $$;
create trigger authorization_audit_immutable before update or delete on authorization_audit for each row execute function authorization_immutable_history();
create trigger authorization_security_events_immutable before update or delete on authorization_security_events for each row execute function authorization_immutable_history();
create trigger authorization_role_history_immutable before update or delete on authorization_roles__history for each row execute function authorization_immutable_history();
create trigger authorization_scope_history_immutable before update or delete on authorization_scopes__history for each row execute function authorization_immutable_history();
create trigger authorization_catalog_history_immutable before update or delete on authorization_catalog_revisions for each row execute function authorization_immutable_history();

-- A PENDING invitation cannot replace the last currently ACTIVE recovery operator.
create or replace function authorization_guard_root_identity() returns trigger language plpgsql as $$
declare affected_scope uuid;
begin
 perform pg_advisory_xact_lock(810082);
 if tg_op='UPDATE' and new.status<>'DISABLED' then return new; end if;
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
create or replace function authorization_guard_assignment() returns trigger language plpgsql as $$
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
   and a.active and r.active and r.name='ROOT' and i.status='ACTIVE') then
  raise exception 'Last recovery assignment is protected' using errcode='23514';
 end if;
 if tg_op='DELETE' then return old; end if;
 return new;
end $$;
