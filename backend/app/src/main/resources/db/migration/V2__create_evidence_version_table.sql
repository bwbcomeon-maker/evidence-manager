-- 证据版本表 evidence_version
-- 用于记录证据的版本历史，每次上传新版本都会创建一条记录

CREATE TABLE IF NOT EXISTS evidence_version (
    id BIGSERIAL PRIMARY KEY,
    evidence_id BIGINT NOT NULL REFERENCES evidence_item(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    original_filename TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    content_type TEXT,
    uploader_id UUID NOT NULL REFERENCES auth_user(id),
    remark TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(evidence_id, version_no)
);

COMMENT ON TABLE evidence_version IS '证据版本表';
COMMENT ON COLUMN evidence_version.id IS '版本记录ID';
COMMENT ON COLUMN evidence_version.evidence_id IS '所属证据ID';
COMMENT ON COLUMN evidence_version.project_id IS '所属项目ID（冗余字段，方便查询）';
COMMENT ON COLUMN evidence_version.version_no IS '版本号（从1开始递增）';
COMMENT ON COLUMN evidence_version.original_filename IS '原始文件名';
COMMENT ON COLUMN evidence_version.file_path IS '文件存储相对路径';
COMMENT ON COLUMN evidence_version.file_size IS '文件大小（字节）';
COMMENT ON COLUMN evidence_version.content_type IS '文件类型（MIME类型）';
COMMENT ON COLUMN evidence_version.uploader_id IS '上传人ID';
COMMENT ON COLUMN evidence_version.remark IS '版本备注';
COMMENT ON COLUMN evidence_version.created_at IS '创建时间';

-- 创建索引（若已存在则跳过）
CREATE INDEX IF NOT EXISTS idx_evidence_version_evidence_id ON evidence_version(evidence_id);
CREATE INDEX IF NOT EXISTS idx_evidence_version_project_id ON evidence_version(project_id);
CREATE INDEX IF NOT EXISTS idx_evidence_version_created_at ON evidence_version(created_at);
