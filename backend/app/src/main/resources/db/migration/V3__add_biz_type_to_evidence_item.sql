-- 为 evidence_item 表新增业务证据类型字段 biz_type
-- 用于区分业务类型：方案/报告/纪要/测试/验收等

-- 1. 添加 biz_type 字段
ALTER TABLE evidence_item 
ADD COLUMN IF NOT EXISTS biz_type VARCHAR(50) NOT NULL DEFAULT 'OTHER';

-- 2. 添加字段注释
COMMENT ON COLUMN evidence_item.biz_type IS '业务证据类型：方案/报告/纪要/测试/验收/OTHER等';

-- 3. 创建组合索引（可选，用于按项目+业务类型查询）
CREATE INDEX IF NOT EXISTS idx_evidence_item_project_id_biz_type ON evidence_item(project_id, biz_type);
