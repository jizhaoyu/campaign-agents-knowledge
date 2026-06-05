insert into app_role (id, code, name, created_at, updated_at) values
    (1, 'ADMIN', '管理员', current_timestamp, current_timestamp),
    (2, 'USER', '普通用户', current_timestamp, current_timestamp),
    (3, 'SUPPORT', '支持工程师', current_timestamp, current_timestamp),
    (4, 'APPROVER', '审批人', current_timestamp, current_timestamp);

insert into app_user (id, username, password_hash, display_name, status, created_at, updated_at) values
    (1, 'admin', '{noop}admin123', '系统管理员', 'ACTIVE', current_timestamp, current_timestamp),
    (2, 'user', '{noop}user123', '普通用户', 'ACTIVE', current_timestamp, current_timestamp),
    (3, 'support', '{noop}support123', '支持工程师', 'ACTIVE', current_timestamp, current_timestamp),
    (4, 'approver', '{noop}approver123', '审批人', 'ACTIVE', current_timestamp, current_timestamp);

insert into app_user_role (user_id, role_id, created_at, updated_at) values
    (1, 1, current_timestamp, current_timestamp),
    (2, 2, current_timestamp, current_timestamp),
    (3, 3, current_timestamp, current_timestamp),
    (4, 4, current_timestamp, current_timestamp);
