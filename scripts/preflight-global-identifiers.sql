-- Read only. Reports resource IDs, never credentials or the identifier values themselves.
begin read only;
with candidates as (
 select 'IDENTITY' source,id,'NICKNAME' key_type,lower(btrim(nickname)) value from ima_identities
 union all select 'IDENTITY',id,'EMAIL',lower(btrim(primary_email)) from ima_identities
 union all select 'APPLICATION',id,'NICKNAME',lower(btrim(nickname)) from ima_identity_applications where status='PENDING'
 union all select 'APPLICATION',id,'EMAIL',lower(btrim(email)) from ima_identity_applications where status='PENDING'
), conflicts as (select key_type,value from candidates where value is not null group by key_type,value having count(*)>1)
select c.source,c.id,c.key_type from candidates c join conflicts d using(key_type,value) order by key_type,source,id;
commit;
