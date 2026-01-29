-- ============================================================
-- 项目交付证据管理系统：用户表与审计日志表
-- Flyway 迁移脚本（PostgreSQL）
-- ============================================================

-- 启用 pgcrypto 扩展（用于 BCrypt 密码哈希，与 Java BCrypt 兼容）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------
-- 1. sys_user 用户表
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    real_name       VARCHAR(64),
    phone           VARCHAR(32),
    email           VARCHAR(128),
    role_code       VARCHAR(32) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    is_deleted      BOOLEAN NOT NULL DEFAULT false,
    last_login_at   TIMESTAMP,
    last_login_ip   VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id IS '用户主键ID';
COMMENT ON COLUMN sys_user.username IS '登录账号（唯一）';
COMMENT ON COLUMN sys_user.password_hash IS '密码哈希（BCrypt）';
COMMENT ON COLUMN sys_user.real_name IS '姓名';
COMMENT ON COLUMN sys_user.phone IS '手机号';
COMMENT ON COLUMN sys_user.email IS '邮箱';
COMMENT ON COLUMN sys_user.role_code IS '角色编码（SYSTEM_ADMIN/PROJECT_OWNER/PROJECT_EDITOR/PROJECT_VIEWER/PROJECT_AUDITOR）';
COMMENT ON COLUMN sys_user.enabled IS '是否启用（禁用不可登录）';
COMMENT ON COLUMN sys_user.is_deleted IS '是否逻辑删除';
COMMENT ON COLUMN sys_user.last_login_at IS '最近登录时间';
COMMENT ON COLUMN sys_user.last_login_ip IS '最近登录IP';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';

-- username 唯一索引（表级 UNIQUE 约束已自动创建唯一索引）

-- ------------------------------------------------------------
-- 2. audit_log 审计日志表
-- ------------------------------------------------------------
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   BIGINT,
    action          VARCHAR(64) NOT NULL,
    target_type     VARCHAR(32),
    target_id       BIGINT,
    success         BOOLEAN NOT NULL,
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    detail          TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE audit_log IS '审计日志表';
COMMENT ON COLUMN audit_log.id IS '审计日志ID';
COMMENT ON COLUMN audit_log.actor_user_id IS '操作人用户ID（登录失败可为空）';
COMMENT ON COLUMN audit_log.action IS '操作类型（如LOGIN_SUCCESS/LOGIN_FAIL/USER_CREATE等）';
COMMENT ON COLUMN audit_log.target_type IS '对象类型（USER/PROJECT/EVIDENCE等）';
COMMENT ON COLUMN audit_log.target_id IS '对象ID';
COMMENT ON COLUMN audit_log.success IS '是否成功';
COMMENT ON COLUMN audit_log.ip IS '来源IP';
COMMENT ON COLUMN audit_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN audit_log.detail IS '操作详情（JSON字符串）';
COMMENT ON COLUMN audit_log.created_at IS '操作时间';

-- 审计日志查询索引
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_actor_user_id ON audit_log (actor_user_id);

-- ------------------------------------------------------------
-- 3. 初始管理员账号：admin / Admin@12345
-- 使用 pgcrypto 的 crypt + gen_salt('bf') 生成 BCrypt 哈希，与 Java BCrypt 兼容
-- 若 admin 已存在则跳过（ON CONFLICT DO NOTHING）
-- ------------------------------------------------------------
INSERT INTO sys_user (
    username,
    password_hash,
    real_name,
    role_code,
    enabled,
    is_deleted
) VALUES (
    'admin',
    crypt('Admin@12345', gen_salt('bf', 10)),
    '系统管理员',
    'SYSTEM_ADMIN',
    true,
    false
) ON CONFLICT (username) DO NOTHING;
