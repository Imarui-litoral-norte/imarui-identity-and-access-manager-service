-- V15 precedes the new internal manifest on a V10 upgrade. First authority only:
-- any historical delegation, including revocation, prevents automatic resurrection.
select pg_advisory_xact_lock(810082);
insert into authorization_permissions(code,description,module,service_code,namespace,catalog_version,updated_at)
 select code,replace(code,'_',' '),'AUTHORIZATION','iiam','iiam',2,current_timestamp
 from (values ('AUTHORIZATION_SCOPE_MANAGE'),('AUTHORIZATION_ROLE_MANAGE'),
 ('AUTHORIZATION_ASSIGNMENT_MANAGE'),('AUTHORIZATION_DENY_MANAGE'),
 ('AUTHORIZATION_DELEGATION_MANAGE'),('AUTHORIZATION_CATALOG_MANAGE')) p(code)
 on conflict(code) do nothing;
insert into authorization_role_permissions(role_id,permission_id)
 select r.id,p.id from authorization_roles r cross join authorization_permissions p
 where r.name='ROOT' and p.service_code='iiam' and p.code in
 ('AUTHORIZATION_SCOPE_MANAGE','AUTHORIZATION_ROLE_MANAGE','AUTHORIZATION_ASSIGNMENT_MANAGE',
  'AUTHORIZATION_DENY_MANAGE','AUTHORIZATION_DELEGATION_MANAGE','AUTHORIZATION_CATALOG_MANAGE')
 on conflict do nothing;
insert into authorization_permission_delegations(id,identity_id,permission_code,scope_id,redelegable,active,reason,created_at,updated_at)
 select gen_random_uuid(),a.identity_id,p.code,a.authorization_scope_id,true,true,
 'V23 explicit legacy ROOT administrative delegation upgrade',current_timestamp,current_timestamp
 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
 cross join authorization_permissions p where a.active and r.active and r.name='ROOT'
 and p.service_code='iiam' and p.code in
 ('AUTHORIZATION_SCOPE_MANAGE','AUTHORIZATION_ROLE_MANAGE','AUTHORIZATION_ASSIGNMENT_MANAGE',
  'AUTHORIZATION_DENY_MANAGE','AUTHORIZATION_DELEGATION_MANAGE','AUTHORIZATION_CATALOG_MANAGE')
 and not exists(select 1 from authorization_permission_delegations d
  where d.identity_id=a.identity_id and d.permission_code=p.code and d.scope_id=a.authorization_scope_id);
insert into authorization_audit(id,service_code,action,resource_type,resource_id,scope_id,occurred_at,reason,correlation_id,after_state)
 select gen_random_uuid(),'iiam-migration','LEGACY_ROOT_GOVERNANCE_UPGRADED','PERMISSION_DELEGATION',id::text,
 scope_id,current_timestamp,reason,gen_random_uuid(),to_jsonb(d)
 from authorization_permission_delegations d where reason='V23 explicit legacy ROOT administrative delegation upgrade';
