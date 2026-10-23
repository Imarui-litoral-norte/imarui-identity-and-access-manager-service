-- Defaults were used only to backfill V9 data safely. New policy writes must specify their boundary and owner.
alter table authorization_identity_roles alter column authorization_scope_id drop default;
alter table authorization_roles alter column ownership drop default;
alter table authorization_permissions alter column service_code drop default;
alter table authorization_permissions alter column namespace drop default;
alter table authorization_permissions alter column catalog_version drop default;
alter table authorization_permissions add constraint ck_external_permission_namespace
 check(service_code='iiam' or starts_with(code,namespace||'.'));
