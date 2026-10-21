-- Legacy rows remain readable; all new HTTP registrations require a nickname.
alter table ima_identities add column nickname varchar(64);
alter table ima_identity_applications add column nickname varchar(64);
alter table ima_identities add constraint ck_identity_nickname check (nickname is null or (nickname = lower(btrim(nickname)) and nickname ~ '^[a-z0-9_.-]{3,64}$' and nickname ~ '[a-z]'));
create unique index uk_identity_kind_nickname on ima_identities(kind, lower(nickname));
create unique index uk_pending_kind_nickname on ima_identity_applications(identity_kind, lower(nickname)) where status='PENDING';

alter table ima_identity_applications alter column email drop not null;
alter table ima_identity_applications add constraint ck_application_contact check(identity_kind='SERVICE' or email is not null);
alter table ima_identities alter column primary_email_id drop not null;
alter table ima_identities alter column primary_email drop not null;
alter table ima_identities alter column primary_email_type drop not null;
alter table ima_identities alter column primary_email_verified drop not null;
alter table ima_identities alter column primary_email_created_at drop not null;
alter table ima_identities add constraint ck_identity_contact check(kind='SERVICE' or (primary_email is not null and primary_email_id is not null and primary_email_type is not null and primary_email_created_at is not null));
