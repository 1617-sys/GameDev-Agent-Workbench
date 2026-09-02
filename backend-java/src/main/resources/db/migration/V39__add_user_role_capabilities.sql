alter table sys_user
    add column role varchar(32) not null default 'USER' after status;

create index idx_sys_user_role
    on sys_user (role);
