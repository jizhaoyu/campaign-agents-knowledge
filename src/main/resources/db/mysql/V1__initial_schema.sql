create table app_user (
    id bigint not null auto_increment,
    username varchar(64) not null,
    password_hash varchar(255) not null,
    display_name varchar(64) not null,
    status varchar(16) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_app_user_username (username)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table app_role (
    id bigint not null auto_increment,
    code varchar(64) not null,
    name varchar(64) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_app_role_code (code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table app_user_role (
    id bigint not null auto_increment,
    user_id bigint not null,
    role_id bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_app_user_role_user_id (user_id),
    key idx_app_user_role_role_id (role_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table knowledge_base (
    id bigint not null auto_increment,
    name varchar(128) not null,
    description varchar(255),
    status varchar(16) not null,
    created_by bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_knowledge_base_created_by (created_by)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table document_record (
    id bigint not null auto_increment,
    knowledge_base_id bigint not null,
    file_name varchar(255) not null,
    file_type varchar(32) not null,
    object_key varchar(255) not null,
    parse_status varchar(16) not null,
    index_status varchar(16) not null,
    uploaded_by bigint not null,
    failure_reason varchar(512),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_document_record_kb_id (knowledge_base_id),
    key idx_document_record_uploaded_by (uploaded_by)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table document_chunk (
    id bigint not null auto_increment,
    document_id bigint not null,
    knowledge_base_id bigint not null,
    chunk_no int not null,
    content longtext not null,
    embedding_json longtext,
    token_count int not null,
    start_offset int not null,
    end_offset int not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_document_chunk_kb_id (knowledge_base_id),
    key idx_document_chunk_document_id (document_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table conversation (
    id bigint not null auto_increment,
    user_id bigint not null,
    knowledge_base_id bigint not null,
    status varchar(16) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_conversation_user_id (user_id),
    key idx_conversation_kb_id (knowledge_base_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table message_record (
    id bigint not null auto_increment,
    conversation_id bigint not null,
    role varchar(16) not null,
    content longtext not null,
    citation_json longtext,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_message_record_conversation_id (conversation_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table ticket (
    id bigint not null auto_increment,
    conversation_id bigint not null,
    title varchar(255) not null,
    description longtext not null,
    priority varchar(16) not null,
    status varchar(32) not null,
    assignee_id bigint,
    created_by bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_ticket_conversation_id (conversation_id),
    key idx_ticket_created_by (created_by),
    key idx_ticket_assignee_id (assignee_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table approval_task (
    id bigint not null auto_increment,
    target_type varchar(32) not null,
    target_id bigint not null,
    approver_id bigint not null,
    status varchar(16) not null,
    comment varchar(255),
    decided_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_approval_task_target (target_type, target_id),
    key idx_approval_task_approver_id (approver_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table audit_log (
    id bigint not null auto_increment,
    actor_id bigint,
    event_type varchar(64) not null,
    target_type varchar(32),
    target_id bigint,
    trace_id varchar(64) not null,
    payload_json longtext,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_audit_log_trace_id (trace_id),
    key idx_audit_log_target (target_type, target_id),
    key idx_audit_log_actor_id (actor_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
