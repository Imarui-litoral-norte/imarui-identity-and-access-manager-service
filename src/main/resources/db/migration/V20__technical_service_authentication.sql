-- Technical security identities only: no application routing/runtime/Tenant enablement.
create table authentication_service_clients (
 service_code varchar(50) primary key references authorization_catalog_owners(service_code),
 client_id uuid not null unique, enabled boolean not null, operations varchar(100) not null,
 created_at timestamptz not null, updated_at timestamptz not null, version bigint not null default 0
);
create table authentication_service_keys (
 id uuid primary key, service_code varchar(50) not null references authentication_service_clients(service_code),
 public_key text not null, fingerprint varchar(64) not null, active boolean not null,
 created_at timestamptz not null, expires_at timestamptz not null, revoked_at timestamptz,
 check(expires_at>created_at), unique(service_code,fingerprint)
);
create index ix_service_key_live on authentication_service_keys(service_code) where active;
create table authentication_service_nonces (
 credential_id uuid not null references authentication_service_keys(id), nonce uuid not null,
 expires_at timestamptz not null, primary key(credential_id,nonce)
);
create index ix_service_nonce_expiration on authentication_service_nonces(expires_at);
create table authorization_service_jurisdictions (
 service_code varchar(50) not null references authentication_service_clients(service_code),
 scope_id uuid not null references authorization_scopes(id), primary key(service_code,scope_id)
);
create table authorization_service_resources (
 service_code varchar(50) not null references authentication_service_clients(service_code),
 resource_type varchar(50) not null, resource_id varchar(150) not null,
 scope_id uuid not null references authorization_scopes(id), created_at timestamptz not null,
 primary key(service_code,resource_type,resource_id),
 check(resource_type<>'TENANT')
);
create index ix_service_resource_scope on authorization_service_resources(scope_id,service_code);
create table authorization_security_events (
 id uuid primary key, event varchar(60) not null, actor_identity_id uuid, service_code varchar(50),
 resource_id varchar(150), scope_id uuid, occurred_at timestamptz not null,
 reason_code varchar(80) not null, correlation_id uuid not null, details jsonb not null default '{}'::jsonb
);
create index ix_security_event_scope_time on authorization_security_events(scope_id,occurred_at desc);
create index ix_security_event_correlation on authorization_security_events(correlation_id);

-- Explicit one-time upgrade of trusted INTERNAL governance, never external manifests.
insert into authorization_permissions(code,description,module,service_code,namespace,catalog_version,updated_at)
 select code,replace(code,'_',' '),module,'iiam','iiam',2,current_timestamp
 from (values ('AUTHORIZATION_AUDIT_READ','AUTHORIZATION'),
 ('AUTHORIZATION_ROOT_RECOVERY','AUTHORIZATION'),('AUTHENTICATION_SERVICE_MANAGE','AUTHENTICATION')) p(code,module)
 on conflict(code) do nothing;
insert into authorization_role_permissions(role_id,permission_id)
 select r.id,p.id from authorization_roles r cross join authorization_permissions p
 where r.name='ROOT' and p.service_code='iiam'
 and p.code in ('AUTHORIZATION_AUDIT_READ','AUTHORIZATION_ROOT_RECOVERY','AUTHENTICATION_SERVICE_MANAGE')
 on conflict do nothing;
insert into authorization_permission_delegations(id,identity_id,permission_code,scope_id,redelegable,active,reason,created_at,updated_at)
 select gen_random_uuid(),a.identity_id,p.code,a.authorization_scope_id,true,true,
 'V20 explicit internal recovery and technical-client governance',current_timestamp,current_timestamp
 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
 cross join authorization_permissions p where a.active and r.active and r.name='ROOT'
 and p.service_code='iiam' and p.code in ('AUTHORIZATION_AUDIT_READ','AUTHORIZATION_ROOT_RECOVERY','AUTHENTICATION_SERVICE_MANAGE')
 and not exists(select 1 from authorization_permission_delegations d where d.identity_id=a.identity_id
   and d.permission_code=p.code and d.scope_id=a.authorization_scope_id);
insert into authorization_audit(id,service_code,action,resource_type,resource_id,scope_id,occurred_at,reason,correlation_id,after_state)
 select gen_random_uuid(),'iiam-migration','INTERNAL_GOVERNANCE_UPGRADED','PERMISSION_DELEGATION',id::text,
 scope_id,current_timestamp,reason,gen_random_uuid(),to_jsonb(d)
 from authorization_permission_delegations d where reason='V20 explicit internal recovery and technical-client governance';
