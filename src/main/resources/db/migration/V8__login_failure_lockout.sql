alter table app_user add column failed_login_count int not null default 0;
alter table app_user add column locked_until timestamp;
