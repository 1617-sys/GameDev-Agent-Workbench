CREATE DATABASE IF NOT EXISTS gamedev_agent_workbench
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE gamedev_agent_workbench;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键 ID',
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户对外 UUID',
  username VARCHAR(50) NOT NULL COMMENT '用户名',
  email VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  password_hash VARCHAR(255) NOT NULL COMMENT '加密后的密码',
  nickname VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
  avatar_url VARCHAR(500) DEFAULT NULL COMMENT '头像地址',
  status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '用户状态',
  last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_user_uuid (user_uuid),
  UNIQUE KEY uk_sys_user_username (username),
  UNIQUE KEY uk_sys_user_email (email)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统用户表';
