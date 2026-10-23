-- Equivalent active blocks have exactly the same effective denial. Keep the oldest ID
-- authoritative and retire duplicates, preserving every row and its original reason.
select pg_advisory_xact_lock(810082);
create temporary table authorization_duplicate_denies on commit drop as
 select *, first_value(id) over w as canonical_id, row_number() over w as duplicate_order
 from authorization_denies where active
 window w as (partition by permission_code,scope_id,identity_id,role_id order by created_at,id);
insert into authorization_audit(id,service_code,action,resource_type,resource_id,scope_id,
 occurred_at,reason,correlation_id,before_state,after_state)
 select gen_random_uuid(),'iiam-migration','DUPLICATE_DENY_RECONCILED','DENY',id::text,scope_id,
 current_timestamp,'V18 equivalent DENY reconciliation; canonical='||canonical_id,gen_random_uuid(),
 to_jsonb(d)-'canonical_id'-'duplicate_order',
 (to_jsonb(d)-'canonical_id'-'duplicate_order')||jsonb_build_object('active',false,'version',version+1,'updated_at',current_timestamp)
 from authorization_duplicate_denies d where duplicate_order>1;
update authorization_denies d set active=false,version=d.version+1,updated_at=greatest(current_timestamp,d.updated_at)
 from authorization_duplicate_denies duplicates where d.id=duplicates.id and duplicates.duplicate_order>1;
create unique index uk_active_deny_identity on authorization_denies(identity_id,permission_code,scope_id)
 where active and identity_id is not null;
create unique index uk_active_deny_role on authorization_denies(role_id,permission_code,scope_id)
 where active and role_id is not null;
