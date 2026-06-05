create table auth_token_session (
    id bigint not null auto_increment,
    token_hash varchar(64) not null,
    user_id bigint not null,
    username varchar(64) not null,
    role_codes varchar(255) not null,
    issued_at datetime(6) not null,
    expires_at datetime(6) not null,
    revoked_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_auth_token_session_token_hash (token_hash),
    key idx_auth_token_session_user_id (user_id),
    key idx_auth_token_session_expires_at (expires_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
