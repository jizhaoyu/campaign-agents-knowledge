alter table auth_token_session add column refresh_token_hash varchar(64);
alter table auth_token_session add column refresh_expires_at timestamp;
alter table auth_token_session add column last_refreshed_at timestamp;

create unique index uk_auth_token_session_refresh_token_hash on auth_token_session (refresh_token_hash);
create index idx_auth_token_session_refresh_expires_at on auth_token_session (refresh_expires_at);
