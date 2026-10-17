-- Email uniqueness is scoped by IdentityKind, including pending applications.
create unique index uk_identity_kind_email_ci on ima_identities(kind, lower(primary_email));
create unique index uk_application_pending_kind_email on ima_identity_applications(identity_kind, lower(email)) where status = 'PENDING';
create unique index uk_application_pending_cpf on ima_identity_applications(cpf) where status = 'PENDING' and cpf is not null;
create unique index uk_application_pending_cnpj on ima_identity_applications(cnpj) where status = 'PENDING' and cnpj is not null;
