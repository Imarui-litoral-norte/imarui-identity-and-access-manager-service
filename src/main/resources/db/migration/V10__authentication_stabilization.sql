-- Preserve published V1-V9 checksums. No invented contact/nickname backfill.
-- Preflight fails safely if an unexpected shared environment contains incomplete registrations.
alter table ima_identities alter column nickname set not null;
alter table ima_identity_applications alter column nickname set not null;
alter table ima_identity_applications alter column email set not null;
alter table ima_identities alter column primary_email set not null;
alter table ima_identities alter column primary_email_id set not null;
alter table ima_identities alter column primary_email_type set not null;
alter table ima_identities alter column primary_email_verified set not null;
alter table ima_identities alter column primary_email_created_at set not null;
drop index if exists uk_identity_kind_email_ci;
drop index if exists uk_application_pending_kind_email;
alter table authentication_audit add column actor_identity_id uuid;
alter table authentication_audit add column target_identity_id uuid;
alter table authentication_audit add column result varchar(20);
alter table authentication_audit add column request_origin varchar(100);
create index idx_auth_audit_actor_target on authentication_audit(actor_identity_id,target_identity_id,occurred_at);
alter table authentication_notification_jobs add column state varchar(20) not null default 'PENDING';
alter table authentication_notification_jobs add column finished_at timestamptz;
update authentication_notification_jobs set state=case when delivered_at is not null then 'DELIVERED' when attempts>=5 then 'FAILED' else 'PENDING' end,
finished_at=case when delivered_at is not null then delivered_at when attempts>=5 then next_attempt_at else null end;
alter table authentication_notification_jobs add constraint ck_notification_state check(state in ('PENDING','PROCESSING','DELIVERED','FAILED','CANCELLED'));
alter table authentication_notification_jobs add constraint ck_notification_attempts check(attempts>=0 and (attempts<=5 or state in ('FAILED','DELIVERED')));
drop index uk_notification_pending;
drop index idx_notification_due;
create unique index uk_notification_pending on authentication_notification_jobs(identity_id,purpose) where state in ('PENDING','PROCESSING');
create index idx_notification_due on authentication_notification_jobs(next_attempt_at) where state in ('PENDING','PROCESSING');

alter table ima_identity_applications add constraint ck_application_nickname check(nickname=lower(btrim(nickname)) and nickname ~ '^[a-z0-9_.-]{3,64}$' and nickname ~ '[a-z]');
