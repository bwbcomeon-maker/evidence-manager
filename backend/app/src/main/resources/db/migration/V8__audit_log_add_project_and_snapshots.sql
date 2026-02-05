-- 审计日志增量增强：项目ID与变更前后快照（可选字段，不影响现有数据）
-- project.id 为 BIGINT，故 project_id 使用 BIGINT 并可为空
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS project_id BIGINT NULL;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS before_data JSONB NULL;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS after_data JSONB NULL;

COMMENT ON COLUMN audit_log.project_id IS '关联项目ID（与 project.id 类型一致，如证据作废、项目成员变更等）';
COMMENT ON COLUMN audit_log.before_data IS '变更前数据快照（JSON）';
COMMENT ON COLUMN audit_log.after_data IS '变更后数据快照（JSON）';
