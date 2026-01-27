-- 项目交付证据管理系统数据库初始化脚本
-- MVP版本

-- 1. 用户表 auth_user
CREATE TABLE auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    email TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth_user IS '系统用户表';
COMMENT ON COLUMN auth_user.id IS '用户ID';
COMMENT ON COLUMN auth_user.username IS '登录名';
COMMENT ON COLUMN auth_user.display_name IS '显示名称';
COMMENT ON COLUMN auth_user.email IS '联系邮箱';
COMMENT ON COLUMN auth_user.is_active IS '是否启用';
COMMENT ON COLUMN auth_user.created_at IS '创建时间';

-- 2. 项目表 project
CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_by UUID NOT NULL REFERENCES auth_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE project IS '项目表';
COMMENT ON COLUMN project.id IS '项目ID';
COMMENT ON COLUMN project.code IS '项目编号';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.description IS '项目描述';
COMMENT ON COLUMN project.status IS '项目状态：active / archived';
COMMENT ON COLUMN project.created_by IS '创建人';
COMMENT ON COLUMN project.created_at IS '创建时间';
COMMENT ON COLUMN project.updated_at IS '更新时间';

-- 创建项目编号唯一索引
CREATE UNIQUE INDEX idx_project_code ON project(code);

-- 3. 项目权限表 auth_project_acl
CREATE TABLE auth_project_acl (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('owner', 'editor', 'viewer')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, user_id)
);

COMMENT ON TABLE auth_project_acl IS '项目权限表';
COMMENT ON COLUMN auth_project_acl.id IS '记录ID';
COMMENT ON COLUMN auth_project_acl.project_id IS '所属项目';
COMMENT ON COLUMN auth_project_acl.user_id IS '授权用户';
COMMENT ON COLUMN auth_project_acl.role IS '项目角色：owner / editor / viewer';
COMMENT ON COLUMN auth_project_acl.created_at IS '授权时间';

-- 创建索引
CREATE INDEX idx_auth_project_acl_project_id ON auth_project_acl(project_id);
CREATE INDEX idx_auth_project_acl_user_id ON auth_project_acl(user_id);

-- 4. 证据元数据表 evidence_item
CREATE TABLE evidence_item (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    note TEXT,
    bucket TEXT NOT NULL,
    object_key TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT NOT NULL,
    etag TEXT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'invalid', 'archived')),
    created_by UUID NOT NULL REFERENCES auth_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invalid_reason TEXT,
    invalid_by UUID REFERENCES auth_user(id),
    invalid_at TIMESTAMPTZ
);

COMMENT ON TABLE evidence_item IS '证据元数据表';
COMMENT ON COLUMN evidence_item.id IS '证据ID';
COMMENT ON COLUMN evidence_item.project_id IS '所属项目';
COMMENT ON COLUMN evidence_item.title IS '证据标题';
COMMENT ON COLUMN evidence_item.note IS '证据说明';
COMMENT ON COLUMN evidence_item.bucket IS '存储桶';
COMMENT ON COLUMN evidence_item.object_key IS '对象路径';
COMMENT ON COLUMN evidence_item.content_type IS '文件类型';
COMMENT ON COLUMN evidence_item.size_bytes IS '文件大小（字节）';
COMMENT ON COLUMN evidence_item.etag IS 'ETag校验标识';
COMMENT ON COLUMN evidence_item.status IS '状态：active / invalid / archived';
COMMENT ON COLUMN evidence_item.created_by IS '上传人';
COMMENT ON COLUMN evidence_item.created_at IS '上传时间';
COMMENT ON COLUMN evidence_item.updated_at IS '更新时间';
COMMENT ON COLUMN evidence_item.invalid_reason IS '误传原因';
COMMENT ON COLUMN evidence_item.invalid_by IS '误传人';
COMMENT ON COLUMN evidence_item.invalid_at IS '误传时间';

-- 创建索引
CREATE INDEX idx_evidence_item_project_id ON evidence_item(project_id);
CREATE INDEX idx_evidence_item_status ON evidence_item(status);
CREATE INDEX idx_evidence_item_created_by ON evidence_item(created_by);

-- 5. 审计日志表 audit_operation_log
CREATE TABLE audit_operation_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES auth_user(id),
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE audit_operation_log IS '操作审计日志表';
COMMENT ON COLUMN audit_operation_log.id IS '日志ID';
COMMENT ON COLUMN audit_operation_log.actor_user_id IS '操作人';
COMMENT ON COLUMN audit_operation_log.action IS '操作类型：upload / mark_invalid 等';
COMMENT ON COLUMN audit_operation_log.target_type IS '目标类型：project / evidence_item';
COMMENT ON COLUMN audit_operation_log.target_id IS '操作对象ID';
COMMENT ON COLUMN audit_operation_log.detail IS '扩展信息（JSON格式）';
COMMENT ON COLUMN audit_operation_log.created_at IS '操作时间';

-- 创建索引
CREATE INDEX idx_audit_operation_log_actor_user_id ON audit_operation_log(actor_user_id);
CREATE INDEX idx_audit_operation_log_target ON audit_operation_log(target_type, target_id);
CREATE INDEX idx_audit_operation_log_created_at ON audit_operation_log(created_at);
