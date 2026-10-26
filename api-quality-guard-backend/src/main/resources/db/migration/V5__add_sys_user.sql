CREATE TABLE sys_user
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32) NOT NULL COMMENT '登录名（唯一）',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name  VARCHAR(50) NOT NULL COMMENT '显示名称',
    role          VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
    enabled       TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用 0-禁用',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_enabled (role, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='平台用户';
