alter table outbox_event
    add column claim_owner varchar(64) null comment 'Publisher 数据库租约 owner' after next_attempt_at,
    add column claim_until datetime null comment 'Publisher 租约过期时间' after claim_owner,
    add column message_id varchar(64) null comment '稳定消息标识' after claim_until,
    add column published_at datetime null comment '发送时间' after message_id,
    add column confirmed_at datetime null comment 'broker confirm 成功时间' after published_at,
    add column last_error_code varchar(80) null comment '最近发布失败码' after confirmed_at,
    add column last_error_message varchar(500) null comment '脱敏发布失败原因' after last_error_code;

create index idx_outbox_event_claim_until on outbox_event (status, claim_until);
