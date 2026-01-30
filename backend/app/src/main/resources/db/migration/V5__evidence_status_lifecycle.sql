-- ============================================================
-- 证据状态机（生命周期）：evidence_status / archived_time / invalid_time
-- 枚举：DRAFT=草稿, SUBMITTED=已提交, ARCHIVED=已归档, INVALID=已作废
-- ============================================================

-- 1. 新增 evidence_status（证据生命周期状态）
ALTER TABLE evidence_item
ADD COLUMN IF NOT EXISTS evidence_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED';

COMMENT ON COLUMN evidence_item.evidence_status IS '证据生命周期状态：DRAFT=草稿（仅保存未提交）, SUBMITTED=已提交（进入管理/审核流程）, ARCHIVED=已归档（最终有效状态）, INVALID=已作废（最终无效状态）';

-- 2. 新增 archived_time（归档时间）
ALTER TABLE evidence_item
ADD COLUMN IF NOT EXISTS archived_time TIMESTAMPTZ NULL;

COMMENT ON COLUMN evidence_item.archived_time IS '归档时间；仅当 evidence_status=ARCHIVED 时有值，表示该证据被归档的时间';

-- 3. 新增 invalid_time（作废时间）
ALTER TABLE evidence_item
ADD COLUMN IF NOT EXISTS invalid_time TIMESTAMPTZ NULL;

COMMENT ON COLUMN evidence_item.invalid_time IS '作废时间；仅当 evidence_status=INVALID 时有值，表示该证据被作废的时间（作废原因 P0-2 再做，此处不增加 invalid_reason）';

-- 4. 历史数据迁移：按原 status 回填 evidence_status 与时间字段
UPDATE evidence_item
SET
  evidence_status = CASE
    WHEN status = 'invalid'  THEN 'INVALID'
    WHEN status = 'archived' THEN 'ARCHIVED'
    ELSE 'SUBMITTED'
  END,
  archived_time = CASE WHEN status = 'archived' THEN COALESCE(updated_at, created_at) ELSE NULL END,
  invalid_time  = CASE WHEN status = 'invalid'  THEN COALESCE(invalid_at, updated_at, created_at) ELSE NULL END
WHERE evidence_status = 'SUBMITTED'
  AND (archived_time IS NULL AND invalid_time IS NULL);

-- 5. 索引（按状态筛选列表时使用）
CREATE INDEX IF NOT EXISTS idx_evidence_item_evidence_status ON evidence_item(evidence_status);
