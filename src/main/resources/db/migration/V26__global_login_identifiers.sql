-- Refuse ambiguous existing data before changing any constraint. No automatic repair.
lock table ima_identities, ima_identity_applications in share row exclusive mode;
do $$
begin
  if exists (
    select lower(btrim(value)) from (
      select nickname value from ima_identities
      union all select nickname from ima_identity_applications where status='PENDING'
    ) candidates where value is not null group by lower(btrim(value)) having count(*)>1
  ) then raise exception 'Global nickname conflicts exist; run the read-only identifier preflight and resolve them explicitly'; end if;
  if exists (
    select lower(btrim(value)) from (
      select primary_email value from ima_identities
      union all select email from ima_identity_applications where status='PENDING'
    ) candidates where value is not null group by lower(btrim(value)) having count(*)>1
  ) then raise exception 'Global email conflicts exist; run the read-only identifier preflight and resolve them explicitly'; end if;
end $$;
drop index if exists uk_identity_kind_nickname;
drop index if exists uk_pending_kind_nickname;
create unique index uk_identity_nickname_global on ima_identities(lower(btrim(nickname)));
create unique index uk_identity_email_global on ima_identities(lower(btrim(primary_email)));
create unique index uk_pending_nickname_global on ima_identity_applications(lower(btrim(nickname))) where status='PENDING';
create unique index uk_pending_email_global on ima_identity_applications(lower(btrim(email))) where status='PENDING';
