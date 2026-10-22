alter table authorization_audit alter column resource_id type varchar(200);
create index ix_authorization_audit_correlation on authorization_audit(correlation_id);
create index ix_authorization_audit_time on authorization_audit(occurred_at desc,id);
create index ix_security_event_time on authorization_security_events(occurred_at desc,id);
create index ix_role_status_owner on authorization_roles(status,ownership,owner_scope_id);
