-- psql -X --set=ON_ERROR_STOP=1 "service=iiam-preflight" --file=scripts/authorization/preflight.sql
-- Read-only inventory. No repair, migration, destructive cleanup or authorization bypass.
\set ON_ERROR_STOP on
begin read only;
set local statement_timeout='10s';
select current_database() as database, current_schema() as schema,
       current_setting('server_version') as postgres_version, pg_database_size(current_database()) as database_bytes;
select to_regclass('flyway_schema_history') is not null as has_flyway \gset
\if :has_flyway
select version,type,script,checksum,success from flyway_schema_history order by installed_rank;
\else
\echo 'No Flyway history in current search_path: stop and confirm database/schema before migration.'
\endif
select c.relname,c.reltuples::bigint as estimated_rows,pg_total_relation_size(c.oid) as total_bytes
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname=current_schema() and c.relkind='r'
  and (c.relname like 'authorization_%' or c.relname like 'authentication_service_%') order by total_bytes desc;
select i.indexrelid::regclass as index_name,i.indisvalid,i.indisready
from pg_index i join pg_class c on c.oid=i.indrelid join pg_namespace n on n.oid=c.relnamespace
where n.nspname=current_schema() and (c.relname like 'authorization_%' or c.relname like 'authentication_service_%');
select locktype,mode,granted,count(*) from pg_locks
where database=(select oid from pg_database where datname=current_database())
  and (locktype<>'advisory' or objid=810082) group by locktype,mode,granted order by locktype,mode;
select to_regclass('authorization_identity_roles') is not null
   and to_regclass('authorization_scopes') is not null as has_authorization \gset
\if :has_authorization
-- Eligibility inventory only; effective recovery also requires the application evaluator, DENY and delegations.
select a.authorization_scope_id,count(distinct i.id) as active_root_identities
from authorization_identity_roles a join authorization_roles r on r.id=a.role_id
join ima_identities i on i.id=a.identity_id
where a.active and r.name='ROOT' and r.active and i.status='ACTIVE' group by a.authorization_scope_id;
\endif
select to_regclass('authorization_denies') is not null as has_denies \gset
\if :has_denies
select count(*) as duplicate_active_deny_groups from (
 select permission_code,scope_id,identity_id,role_id from authorization_denies where active
 group by permission_code,scope_id,identity_id,role_id having count(*)>1
) duplicates;
\endif
commit;
