-- ============================================================
-- A 方案：业务表用户外键统一改为 sys_user.id (BIGINT)
-- 开发阶段可清库：先 TRUNCATE 再改表结构，不再引用 auth_user。
-- auth_user 表保留但不被业务表引用（deprecated）。
-- ============================================================

-- 1) 清空依赖 auth_user 的业务数据（按依赖顺序）
TRUNCATE TABLE evidence_version RESTART IDENTITY CASCADE;
TRUNCATE TABLE evidence_item RESTART IDENTITY CASCADE;
TRUNCATE TABLE auth_project_acl RESTART IDENTITY CASCADE;
TRUNCATE TABLE project RESTART IDENTITY CASCADE;
TRUNCATE TABLE audit_operation_log RESTART IDENTITY CASCADE;

-- 2) project: created_by (UUID) -> created_by_user_id (BIGINT)
ALTER TABLE project DROP CONSTRAINT IF EXISTS project_created_by_fkey;
ALTER TABLE project DROP COLUMN IF EXISTS created_by;
ALTER TABLE project ADD COLUMN created_by_user_id BIGINT NOT NULL REFERENCES sys_user(id);
COMMENT ON COLUMN project.created_by_user_id IS '创建人 sys_user.id';
CREATE INDEX idx_project_created_by_user_id ON project(created_by_user_id);

-- 3) evidence_item: created_by, invalid_by (UUID) -> created_by_user_id, invalid_by_user_id (BIGINT)
ALTER TABLE evidence_item DROP CONSTRAINT IF EXISTS evidence_item_created_by_fkey;
ALTER TABLE evidence_item DROP COLUMN IF EXISTS created_by;
ALTER TABLE evidence_item ADD COLUMN created_by_user_id BIGINT NOT NULL REFERENCES sys_user(id);
COMMENT ON COLUMN evidence_item.created_by_user_id IS '上传人 sys_user.id';

ALTER TABLE evidence_item DROP CONSTRAINT IF EXISTS evidence_item_invalid_by_fkey;
ALTER TABLE evidence_item DROP COLUMN IF EXISTS invalid_by;
ALTER TABLE evidence_item ADD COLUMN invalid_by_user_id BIGINT REFERENCES sys_user(id);
COMMENT ON COLUMN evidence_item.invalid_by_user_id IS '作废人 sys_user.id';

DROP INDEX IF EXISTS idx_evidence_item_created_by;
CREATE INDEX idx_evidence_item_created_by_user_id ON evidence_item(created_by_user_id);

-- 4) evidence_version: uploader_id (UUID) -> uploader_user_id (BIGINT)
ALTER TABLE evidence_version DROP CONSTRAINT IF EXISTS evidence_version_uploader_id_fkey;
ALTER TABLE evidence_version DROP COLUMN IF EXISTS uploader_id;
ALTER TABLE evidence_version ADD COLUMN uploader_user_id BIGINT NOT NULL REFERENCES sys_user(id);
COMMENT ON COLUMN evidence_version.uploader_user_id IS '上传人 sys_user.id';
CREATE INDEX IF NOT EXISTS idx_evidence_version_uploader_user_id ON evidence_version(uploader_user_id);

-- 5) auth_project_acl: user_id (UUID) -> sys_user_id (BIGINT)，唯一约束 (project_id, sys_user_id)
ALTER TABLE auth_project_acl DROP CONSTRAINT IF EXISTS auth_project_acl_project_id_user_id_key;
ALTER TABLE auth_project_acl DROP CONSTRAINT IF EXISTS auth_project_acl_user_id_fkey;
ALTER TABLE auth_project_acl DROP COLUMN IF EXISTS user_id;
ALTER TABLE auth_project_acl ADD COLUMN sys_user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE;
COMMENT ON COLUMN auth_project_acl.sys_user_id IS '授权用户 sys_user.id';
CREATE UNIQUE INDEX idx_auth_project_acl_project_sys_user ON auth_project_acl(project_id, sys_user_id);
DROP INDEX IF EXISTS idx_auth_project_acl_user_id;
CREATE INDEX idx_auth_project_acl_sys_user_id ON auth_project_acl(sys_user_id);

-- 6) audit_operation_log: actor_user_id (UUID) -> actor_user_id (BIGINT)
ALTER TABLE audit_operation_log DROP CONSTRAINT IF EXISTS audit_operation_log_actor_user_id_fkey;
ALTER TABLE audit_operation_log DROP COLUMN IF EXISTS actor_user_id;
ALTER TABLE audit_operation_log ADD COLUMN actor_user_id BIGINT REFERENCES sys_user(id);
COMMENT ON COLUMN audit_operation_log.actor_user_id IS '操作人 sys_user.id';
CREATE INDEX idx_audit_operation_log_actor_user_id ON audit_operation_log(actor_user_id);
