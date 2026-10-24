alter table ima_identity_applications add column approved_kind varchar(30);
update ima_identity_applications set approved_kind=identity_kind where status='APPROVED';
alter table ima_identity_applications add constraint ck_application_approved_kind check (
 (status='APPROVED' and approved_kind is not null and (approved_kind=identity_kind or approved_kind='SERVICE'))
 or (status<>'APPROVED' and approved_kind is null)
);
-- Preserve the full original name when approving a person/company as a service.
alter table ima_service_identities alter column name type varchar(150);
