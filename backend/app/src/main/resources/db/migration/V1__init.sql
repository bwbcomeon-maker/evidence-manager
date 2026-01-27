-- 项目交付证据管理系统 - 数据库初始化脚本
-- 版本: V1
-- 说明: MVP版本数据库表结构

-- ============================================
-- 1. 用户表 auth_user
-- ============================================
CREATE TABLE auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT NOT NULL UNIQUE,
    display_name TEXT,
    email TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户表索引
CREATE INDEX idx_auth_user_username ON auth_user(username);
CREATE INDEX idx_auth_user_is_active ON auth_user(is_active);

COMMENT ON TABLE auth_user IS '系统用户表';
COMMENT ON COLUMN auth_user.id IS '用户ID';
COMMENT ON COLUMN auth_user.username IS '登录名（唯一登录账号）';
COMMENT ON COLUMN auth_user.display_name IS '显示名称（用户姓名或昵称）';
COMMENT ON COLUMN auth_user.email IS '联系邮箱';
COMMENT ON COLUMN auth_user.is_active IS '是否启用';
COMMENT ON COLUMN auth_user.created_at IS '创建时间';

-- ============================================
-- 2. 项目表 project
-- ============================================
CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 项目表索引
CREATE INDEX idx_project_code ON project(code);
CREATE INDEX idx_project_created_by ON project(created_by);
CREATE INDEX idx_project_status ON project(status);

COMMENT ON TABLE project IS '项目表';
COMMENT ON COLUMN project.id IS '项目ID';
COMMENT ON COLUMN project.code IS '项目编号';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.description IS '项目说明';
COMMENT ON COLUMN project.status IS '项目状态（active / archived）';
COMMENT ON COLUMN project.created_by IS '创建用户';
COMMENT ON COLUMN project.created_at IS '创建时间';
COMMENT ON COLUMN project.updated_at IS '更新时间';

-- ============================================
-- 3. 项目权限表 auth_project_acl
-- ============================================
CREATE TABLE auth_project_acl (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('owner', 'editor', 'viewer')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, user_id)
);

-- 项目权限表索引
CREATE INDEX idx_auth_project_acl_project_id ON auth_project_acl(project_id);
CREATE INDEX idx_auth_project_acl_user_id ON auth_project_acl(user_id);
CREATE INDEX idx_auth_project_acl_role ON auth_project_acl(role);

COMMENT ON TABLE auth_project_acl IS '项目权限表';
COMMENT ON COLUMN auth_project_acl.id IS '记录ID';
COMMENT ON COLUMN auth_project_acl.project_id IS '所属项目';
COMMENT ON COLUMN auth_project_acl.user_id IS '授权用户';
COMMENT ON COLUMN auth_project_acl.role IS '项目角色（owner / editor / viewer）';
COMMENT ON COLUMN auth_project_acl.created_at IS '授权时间';

-- ============================================
-- 4. 证据元数据表 evidence_item
-- ============================================
CREATE TABLE evidence_item (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title TEXT,
    note TEXT,
    bucket TEXT NOT NULL,
    object_key TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT,
    etag TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'invalid', 'archived')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invalid_reason TEXT,
    invalid_by UUID,
    invalid_at TIMESTAMPTZ,
    UNIQUE (bucket, object_key)
);

-- 证据元数据表索引
CREATE INDEX idx_evidence_item_project_id ON evidence_item(project_id);
CREATE INDEX idx_evidence_item_created_by ON evidence_item(created_by);
CREATE INDEX idx_evidence_item_status ON evidence_item(status);
CREATE INDEX idx_evidence_item_bucket_object_key ON evidence_item(bucket, object_key);

COMMENT ON TABLE evidence_item IS '证据元数据表';
COMMENT ON COLUMN evidence_item.id IS '证据ID';
COMMENT ON COLUMN evidence_item.project_id IS '所属项目';
COMMENT ON COLUMN evidence_item.title IS '证据标题';
COMMENT ON COLUMN evidence_item.note IS '补充说明';
COMMENT ON COLUMN evidence_item.bucket IS 'MinIO Bucket';
COMMENT ON COLUMN evidence_item.object_key IS 'MinIO Key';
COMMENT ON COLUMN evidence_item.content_type IS 'MIME 类型';
COMMENT ON COLUMN evidence_item.size_bytes IS '字节大小';
COMMENT ON COLUMN evidence_item.etag IS '校验标识';
COMMENT ON COLUMN evidence_item.status IS '状态（active / invalid / archived）';
COMMENT ON COLUMN evidence_item.created_by IS '上传人';
COMMENT ON COLUMN evidence_item.created_at IS '上传时间';
COMMENT ON COLUMN evidence_item.updated_at IS '更新时间';
COMMENT ON COLUMN evidence_item.invalid_reason IS '误传说明';
COMMENT ON COLUMN evidence_item.invalid_by IS '操作人';
COMMENT ON COLUMN evidence_item.invalid_at IS '操作时间';

-- ============================================
-- 5. 操作审计日志表 audit_operation_log
-- ============================================
CREATE TABLE audit_operation_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 操作审计日志表索引
CREATE INDEX idx_audit_operation_log_actor_user_id ON audit_operation_log(actor_user_id);
CREATE INDEX idx_audit_operation_log_action ON audit_operation_log(action);
CREATE INDEX idx_audit_operation_log_target ON audit_operation_log(target_type, target_id);
CREATE INDEX idx_audit_operation_log_created_at ON audit_operation_log(created_at);

COMMENT ON TABLE audit_operation_log IS '操作审计日志表';
COMMENT ON COLUMN audit_operation_log.id IS '日志ID';
COMMENT ON COLUMN audit_operation_log.actor_user_id IS '操作用户';
COMMENT ON COLUMN audit_operation_log.action IS '操作类型（upload / mark_invalid 等）';
COMMENT ON COLUMN audit_operation_log.target_type IS '目标类型（project / evidence_item）';
COMMENT ON COLUMN audit_operation_log.target_id IS '操作对象ID';
COMMENT ON COLUMN audit_operation_log.detail IS '扩展信息（JSONB格式）';
COMMENT ON COLUMN audit_operation_log.created_at IS '操作时间';

-- ============================================
-- 外键约束（可选，根据业务需求决定是否启用）
-- ============================================
-- 注意：以下外键约束已注释，如需启用请取消注释
-- 未启用外键的原因：
-- 1. 使用应用层保证数据完整性
-- 2. 避免级联删除的复杂性
-- 3. 提高性能（减少外键检查开销）

-- ALTER TABLE project ADD CONSTRAINT fk_project_created_by 
--     FOREIGN KEY (created_by) REFERENCES auth_user(id);

-- ALTER TABLE auth_project_acl ADD CONSTRAINT fk_auth_project_acl_project_id 
--     FOREIGN KEY (project_id) REFERENCES project(id);

-- ALTER TABLE auth_project_acl ADD CONSTRAINT fk_auth_project_acl_user_id 
--     FOREIGN KEY (user_id) REFERENCES auth_user(id);

-- ALTER TABLE evidence_item ADD CONSTRAINT fk_evidence_item_project_id 
--     FOREIGN KEY (project_id) REFERENCES project(id);

-- ALTER TABLE evidence_item ADD CONSTRAINT fk_evidence_item_created_by 
--     FOREIGN KEY (created_by) REFERENCES auth_user(id);

-- ALTER TABLE evidence_item ADD CONSTRAINT fk_evidence_item_invalid_by 
--     FOREIGN KEY (invalid_by) REFERENCES auth_user(id);

-- ALTER TABLE audit_operation_log ADD CONSTRAINT fk_audit_operation_log_actor_user_id 
--     FOREIGN KEY (actor_user_id) REFERENCES auth_user(id);
