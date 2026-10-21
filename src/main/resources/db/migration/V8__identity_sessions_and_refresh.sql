alter table authentication_sessions add column identity_id uuid;
alter table authentication_sessions add column public_id uuid;
alter table authentication_sessions add column revoked_at timestamptz;
-- Existing sessions have no provable owner. Preserve records and terminate renewal.
update authentication_sessions set session_status='REVOKED',logged_out_at=null,revoked_at=current_timestamp;
update authentication_refresh_tokens set status='REVOKED',replaced_by_token_id=null;
alter table authentication_sessions add constraint ck_owned_active_session check (session_status<>'ACTIVE' or (identity_id is not null and public_id is not null));
create unique index uk_session_public_id on authentication_sessions(public_id);
create index idx_session_identity on authentication_sessions(identity_id,session_status);
create index idx_session_expiry on authentication_sessions(expires_at);
-- Retain UUID values after physical Identity deletion: no operational Identity FK here.
create index idx_refresh_session_status on authentication_refresh_tokens(session_id,status);
alter table authentication_refresh_tokens add constraint fk_refresh_session foreign key(session_id) references authentication_sessions(id);
alter table authentication_refresh_tokens add constraint fk_refresh_successor foreign key(replaced_by_token_id) references authentication_refresh_tokens(id);
create unique index uk_refresh_successor on authentication_refresh_tokens(replaced_by_token_id);
