create table authorization_catalog_owners (
 service_code varchar(50) primary key, namespace varchar(50) not null unique,
 version bigint not null default 0 check(version>=0), digest varchar(64), updated_at timestamptz not null,
 unique(service_code,namespace), check(service_code ~ '^[a-z][a-z0-9-]{1,49}$'),
 check(namespace ~ '^[a-z][a-z0-9-]{1,49}$'),
 check((version=0 and digest is null) or (version>0 and digest ~ '^[0-9a-f]{64}$'))
);
insert into authorization_catalog_owners(service_code,namespace,updated_at) values('iiam','iiam',current_timestamp);
alter table authorization_permissions add column service_code varchar(50) not null default 'iiam';
alter table authorization_permissions add column namespace varchar(50) not null default 'iiam';
alter table authorization_permissions add column catalog_version bigint not null default 1 check(catalog_version>0);
alter table authorization_permissions add column deprecated boolean not null default false;
alter table authorization_permissions add column updated_at timestamptz;
update authorization_permissions set updated_at=created_at;
alter table authorization_permissions alter column updated_at set not null;
alter table authorization_permissions add constraint fk_permission_owner foreign key(service_code,namespace)
 references authorization_catalog_owners(service_code,namespace);
create index ix_authorization_permission_owner on authorization_permissions(service_code,deprecated);
create table authorization_catalog_revisions (
 service_code varchar(50) not null references authorization_catalog_owners(service_code),
 version bigint not null check(version>0), digest varchar(64) not null,
 recorded_at timestamptz not null, manifest jsonb not null, primary key(service_code,version)
);
