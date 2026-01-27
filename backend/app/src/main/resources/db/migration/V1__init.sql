-- ============================================
-- 项目交付证据管理系统 - 数据库初始化脚本
-- Version: 1.0
-- Description: MVP 版本数据库表结构初始化
-- ============================================

-- ============================================
-- 1. 数据字典表（用于存储枚举值）
-- ============================================
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGSERIAL PRIMARY KEY,
    dict_type VARCHAR(50) NOT NULL,
    dict_code VARCHAR(50) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_label VARCHAR(200) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    remark TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dict_type_code UNIQUE (dict_type, dict_code)
);

COMMENT ON TABLE sys_dict IS '系统数据字典表';
COMMENT ON COLUMN sys_dict.id IS '字典ID';
COMMENT ON COLUMN sys_dict.dict_type IS '字典类型';
COMMENT ON COLUMN sys_dict.dict_code IS '字典编码';
COMMENT ON COLUMN sys_dict.dict_value IS '字典值';
COMMENT ON COLUMN sys_dict.dict_label IS '字典标签';
COMMENT ON COLUMN sys_dict.sort_order IS '排序顺序';
COMMENT ON COLUMN sys_dict.is_active IS '是否启用';
COMMENT ON COLUMN sys_dict.remark IS '备注';
COMMENT ON COLUMN sys_dict.created_at IS '创建时间';
COMMENT ON COLUMN sys_dict.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX idx_dict_type ON sys_dict(dict_type);
CREATE INDEX idx_dict_code ON sys_dict(dict_code);

-- 插入项目状态字典数据
INSERT INTO sys_dict (dict_type, dict_code, dict_value, dict_label, sort_order, remark) VALUES
('project_status', 'active', 'active', '活跃', 1, '项目处于活跃状态'),
('project_status', 'archived', 'archived', '已归档', 2, '项目已归档')
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 插入项目角色字典数据
INSERT INTO sys_dict (dict_type, dict_code, dict_value, dict_label, sort_order, remark) VALUES
('project_role', 'owner', 'owner', '项目负责人', 1, '对项目与证据负最终责任'),
('project_role', 'editor', 'editor', '项目成员', 2, '负责上传、维护证据'),
('project_role', 'viewer', 'viewer', '查看人员', 3, '仅查看、下载证据')
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 插入证据状态字典数据
INSERT INTO sys_dict (dict_type, dict_code, dict_value, dict_label, sort_order, remark) VALUES
('evidence_status', 'active', 'active', '有效', 1, '证据处于有效状态'),
('evidence_status', 'invalid', 'invalid', '误传', 2, '证据被标记为误传'),
('evidence_status', 'archived', 'archived', '已归档', 3, '证据已归档')
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 插入操作类型字典数据
INSERT INTO sys_dict (dict_type, dict_code, dict_value, dict_label, sort_order, remark) VALUES
('operation_action', 'upload', 'upload', '上传证据', 1, '上传证据操作'),
('operation_action', 'mark_invalid', 'mark_invalid', '标记误传', 2, '标记证据为误传'),
('operation_action', 'restore', 'restore', '恢复证据', 3, '恢复误传证据'),
('operation_action', 'archive', 'archive', '归档证据', 4, '归档证据操作'),
('operation_action', 'download', 'download', '下载证据', 5, '下载证据操作'),
('operation_action', 'create_project', 'create_project', '创建项目', 6, '创建项目操作'),
('operation_action', 'grant_permission', 'grant_permission', '授权', 7, '项目授权操作')
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- 插入目标类型字典数据
INSERT INTO sys_dict (dict_type, dict_code, dict_value, dict_label, sort_order, remark) VALUES
('target_type', 'project', 'project', '项目', 1, '操作目标为项目'),
('target_type', 'evidence_item', 'evidence_item', '证据', 2, '操作目标为证据')
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ============================================
-- 2. 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT NOT NULL,
    display_name TEXT NOT NULL,
    email TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_user_username UNIQUE (username)
);

COMMENT ON TABLE auth_user IS '系统用户表';
COMMENT ON COLUMN auth_user.id IS '用户ID';
COMMENT ON COLUMN auth_user.username IS '登录名';
COMMENT ON COLUMN auth_user.display_name IS '显示名称';
COMMENT ON COLUMN auth_user.email IS '邮箱';
COMMENT ON COLUMN auth_user.is_active IS '是否启用';
COMMENT ON COLUMN auth_user.created_at IS '创建时间';

-- 创建索引
CREATE INDEX idx_auth_user_username ON auth_user(username);
CREATE INDEX idx_auth_user_email ON auth_user(email);
CREATE INDEX idx_auth_user_is_active ON auth_user(is_active);

-- ============================================
-- 3. 项目表
-- ============================================
CREATE TABLE IF NOT EXISTS project (
    id BIGSERIAL PRIMARY KEY,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_code UNIQUE (code),
    CONSTRAINT fk_project_created_by FOREIGN KEY (created_by) REFERENCES auth_user(id),
    CONSTRAINT ck_project_status CHECK (status IN ('active', 'archived'))
);

COMMENT ON TABLE project IS '项目表';
COMMENT ON COLUMN project.id IS '项目ID';
COMMENT ON COLUMN project.code IS '项目编号';
COMMENT ON COLUMN project.name IS '项目名称';
COMMENT ON COLUMN project.description IS '项目描述';
COMMENT ON COLUMN project.status IS '项目状态：active-活跃, archived-已归档';
COMMENT ON COLUMN project.created_by IS '创建人';
COMMENT ON COLUMN project.created_at IS '创建时间';
COMMENT ON COLUMN project.updated_at IS '更新时间';

-- 创建索引
CREATE INDEX idx_project_code ON project(code);
CREATE INDEX idx_project_status ON project(status);
CREATE INDEX idx_project_created_by ON project(created_by);

-- ============================================
-- 4. 项目权限表
-- ============================================
CREATE TABLE IF NOT EXISTS auth_project_acl (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    role TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_project_acl_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_auth_project_acl_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_project_acl_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_project_acl_role CHECK (role IN ('owner', 'editor', 'viewer'))
);

COMMENT ON TABLE auth_project_acl IS '项目权限表';
COMMENT ON COLUMN auth_project_acl.id IS '记录ID';
COMMENT ON COLUMN auth_project_acl.project_id IS '项目ID';
COMMENT ON COLUMN auth_project_acl.user_id IS '用户ID';
COMMENT ON COLUMN auth_project_acl.role IS '项目角色：owner-项目负责人, editor-项目成员, viewer-查看人员';
COMMENT ON COLUMN auth_project_acl.created_at IS '授权时间';

-- 创建索引
CREATE INDEX idx_auth_project_acl_project_id ON auth_project_acl(project_id);
CREATE INDEX idx_auth_project_acl_user_id ON auth_project_acl(user_id);
CREATE INDEX idx_auth_project_acl_role ON auth_project_acl(role);

-- ============================================
-- 5. 证据元数据表
-- ============================================
CREATE TABLE IF NOT EXISTS evidence_item (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    note TEXT,
    bucket TEXT NOT NULL,
    object_key TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    etag TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invalid_reason TEXT,
    invalid_by UUID,
    invalid_at TIMESTAMPTZ,
    CONSTRAINT fk_evidence_item_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_evidence_item_created_by FOREIGN KEY (created_by) REFERENCES auth_user(id),
    CONSTRAINT fk_evidence_item_invalid_by FOREIGN KEY (invalid_by) REFERENCES auth_user(id),
    CONSTRAINT ck_evidence_item_status CHECK (status IN ('active', 'invalid', 'archived'))
);

COMMENT ON TABLE evidence_item IS '证据元数据表';
COMMENT ON COLUMN evidence_item.id IS '证据ID';
COMMENT ON COLUMN evidence_item.project_id IS '所属项目';
COMMENT ON COLUMN evidence_item.title IS '证据标题';
COMMENT ON COLUMN evidence_item.note IS '证据说明';
COMMENT ON COLUMN evidence_item.bucket IS '存储桶';
COMMENT ON COLUMN evidence_item.object_key IS '对象路径';
COMMENT ON COLUMN evidence_item.content_type IS '文件类型（MIME类型）';
COMMENT ON COLUMN evidence_item.size_bytes IS '文件大小（字节）';
COMMENT ON COLUMN evidence_item.etag IS 'ETag校验标识';
COMMENT ON COLUMN evidence_item.status IS '状态：active-有效, invalid-误传, archived-已归档';
COMMENT ON COLUMN evidence_item.created_by IS '创建人（上传人）';
COMMENT ON COLUMN evidence_item.created_at IS '创建时间（上传时间）';
COMMENT ON COLUMN evidence_item.updated_at IS '更新时间';
COMMENT ON COLUMN evidence_item.invalid_reason IS '误传原因';
COMMENT ON COLUMN evidence_item.invalid_by IS '误传人';
COMMENT ON COLUMN evidence_item.invalid_at IS '误传时间';

-- 创建索引
CREATE INDEX idx_evidence_item_project_id ON evidence_item(project_id);
CREATE INDEX idx_evidence_item_status ON evidence_item(status);
CREATE INDEX idx_evidence_item_created_by ON evidence_item(created_by);
CREATE INDEX idx_evidence_item_created_at ON evidence_item(created_at DESC);
CREATE INDEX idx_evidence_item_bucket_key ON evidence_item(bucket, object_key);

-- ============================================
-- 6. 审计日志表
-- ============================================
CREATE TABLE IF NOT EXISTS audit_operation_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    detail JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_operation_log_actor FOREIGN KEY (actor_user_id) REFERENCES auth_user(id),
    CONSTRAINT ck_audit_operation_log_action CHECK (action IN ('upload', 'mark_invalid', 'restore', 'archive', 'download', 'create_project', 'grant_permission')),
    CONSTRAINT ck_audit_operation_log_target_type CHECK (target_type IN ('project', 'evidence_item'))
);

COMMENT ON TABLE audit_operation_log IS '操作审计日志表';
COMMENT ON COLUMN audit_operation_log.id IS '日志ID';
COMMENT ON COLUMN audit_operation_log.actor_user_id IS '操作人';
COMMENT ON COLUMN audit_operation_log.action IS '操作类型：upload-上传证据, mark_invalid-标记误传, restore-恢复证据, archive-归档证据, download-下载证据, create_project-创建项目, grant_permission-授权';
COMMENT ON COLUMN audit_operation_log.target_type IS '目标类型：project-项目, evidence_item-证据';
COMMENT ON COLUMN audit_operation_log.target_id IS '操作对象ID';
COMMENT ON COLUMN audit_operation_log.detail IS '操作详情（JSON格式）';
COMMENT ON COLUMN audit_operation_log.created_at IS '操作时间';

-- 创建索引
CREATE INDEX idx_audit_operation_log_actor ON audit_operation_log(actor_user_id);
CREATE INDEX idx_audit_operation_log_action ON audit_operation_log(action);
CREATE INDEX idx_audit_operation_log_target ON audit_operation_log(target_type, target_id);
CREATE INDEX idx_audit_operation_log_created_at ON audit_operation_log(created_at DESC);

-- ============================================
-- 创建更新时间触发器函数
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为 project 表创建更新时间触发器
CREATE TRIGGER trigger_project_updated_at
    BEFORE UPDATE ON project
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 为 evidence_item 表创建更新时间触发器
CREATE TRIGGER trigger_evidence_item_updated_at
    BEFORE UPDATE ON evidence_item
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 为 sys_dict 表创建更新时间触发器
CREATE TRIGGER trigger_sys_dict_updated_at
    BEFORE UPDATE ON sys_dict
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
