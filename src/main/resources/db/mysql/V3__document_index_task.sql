create table document_index_task (
    id bigint not null auto_increment,
    document_id bigint not null,
    status varchar(16) not null,
    attempt_count int not null,
    max_attempts int not null,
    trace_id varchar(64),
    last_error varchar(512),
    next_run_at datetime(6) not null,
    started_at datetime(6),
    finished_at datetime(6),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    key idx_document_index_task_due (status, next_run_at, id),
    key idx_document_index_task_document_id (document_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
