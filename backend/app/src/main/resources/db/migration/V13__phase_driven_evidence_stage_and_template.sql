-- ============================================================
-- 阶段任务驱动证据：阶段表、项目阶段进度表、证据模板表；
-- evidence_item 增加 stage_id/evidence_type_code，删除 status/biz_type；
-- project 增加 has_procurement。
-- 执行顺序建议：需清库时先执行 db/scripts/dev-reset.sql，再启动应用触发本迁移。
-- ============================================================

-- ------------------------------------------------------------
-- 1. delivery_stage 阶段定义表
-- ------------------------------------------------------------
CREATE TABLE delivery_stage (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_delivery_stage_code UNIQUE (code)
);

COMMENT ON TABLE delivery_stage IS '交付阶段定义表';
COMMENT ON COLUMN delivery_stage.id IS '阶段ID';
COMMENT ON COLUMN delivery_stage.code IS '阶段编码，如 S1-S5';
COMMENT ON COLUMN delivery_stage.name IS '阶段名称';
COMMENT ON COLUMN delivery_stage.description IS '阶段说明，供前端展示';
COMMENT ON COLUMN delivery_stage.sort_order IS '排序';

INSERT INTO delivery_stage (code, name, description, sort_order) VALUES
('S1', '项目启动阶段', '启动会、实施计划等', 1),
('S2', '采购与设备到货阶段', '到货、开箱、验收', 2),
('S3', '环境搭建与实施阶段', '上架、安装、施工', 3),
('S4', '联调测试阶段', '测试报告', 4),
('S5', '验收阶段', '验收报告或终验专家评审报告', 5);

-- ------------------------------------------------------------
-- 2. project_stage 项目阶段进度表
-- ------------------------------------------------------------
CREATE TABLE project_stage (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    stage_id        BIGINT NOT NULL REFERENCES delivery_stage(id) ON DELETE RESTRICT,
    status          VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_stage_project_stage UNIQUE (project_id, stage_id)
);

COMMENT ON TABLE project_stage IS '项目阶段进度表';
COMMENT ON COLUMN project_stage.status IS 'NOT_STARTED/IN_PROGRESS/COMPLETED';
COMMENT ON COLUMN project_stage.completed_at IS '标记完成时间';

CREATE INDEX idx_project_stage_project_id ON project_stage(project_id);
CREATE INDEX idx_project_stage_stage_id ON project_stage(stage_id);

-- ------------------------------------------------------------
-- 3. stage_evidence_template 阶段证据模板表
-- ------------------------------------------------------------
CREATE TABLE stage_evidence_template (
    id                      BIGSERIAL PRIMARY KEY,
    stage_id                 BIGINT NOT NULL REFERENCES delivery_stage(id) ON DELETE CASCADE,
    evidence_type_code       VARCHAR(100) NOT NULL,
    display_name             VARCHAR(200) NOT NULL,
    is_required              BOOLEAN NOT NULL DEFAULT true,
    min_count                INT NOT NULL DEFAULT 1,
    sort_order               INT NOT NULL DEFAULT 0,
    required_when            VARCHAR(50),
    rule_group               VARCHAR(50),
    group_required_count     INT,
    CONSTRAINT uk_stage_evidence_template_stage_type UNIQUE (stage_id, evidence_type_code)
);

COMMENT ON TABLE stage_evidence_template IS '阶段证据模板表';
COMMENT ON COLUMN stage_evidence_template.required_when IS '如 HAS_PROCUREMENT：仅当 project.has_procurement=true 时参与必填';
COMMENT ON COLUMN stage_evidence_template.rule_group IS '同组多项二选一/多选一时共享同一值';
COMMENT ON COLUMN stage_evidence_template.group_required_count IS '组内至少需满足的项数';

CREATE INDEX idx_stage_evidence_template_stage_id ON stage_evidence_template(stage_id);

-- S1
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S1_START_PHOTO', '启动现场照片', true, 1, 1, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S1';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S1_START_REPORT', '启动汇报相关材料', true, 1, 2, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S1';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S1_IMPL_PLAN', '项目实施计划', true, 1, 3, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S1';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S1_PRODUCT_COMPARE', '项目前期产品比测报告', true, 1, 4, 'HAS_PROCUREMENT', NULL, NULL FROM delivery_stage WHERE code = 'S1';

-- S2
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_LOGISTICS_SIGNED', '物流单照片签字版', true, 1, 1, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_ARRIVAL_PHOTO', '设备到货现场照片', true, 3, 2, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_PACKAGE_PHOTO', '外包装及配件照片', true, 1, 3, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_NAME_PLATE', '设备铭牌/合格证照片', true, 1, 4, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_ARRIVAL_ACCEPTANCE', '到货验收单用户签字', true, 1, 5, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_ARRIVAL_LIST', '总体到货清单', true, 1, 6, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S2_QUALITY_GUARANTEE', '产品质保证明截图', true, 1, 7, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S2';

-- S3
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S3_INSTALL_PHOTO', '设备上架或软件安装照片', true, 3, 1, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S3';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S3_SITE_PHOTO', '现场施工照片', true, 3, 2, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S3';

-- S4
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S4_TEST_REPORT', '测试报告', true, 1, 1, NULL, NULL, NULL FROM delivery_stage WHERE code = 'S4';

-- S5 二选一
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S5_ACCEPTANCE_REPORT', '验收报告签字版', true, 1, 1, NULL, 'S5_ACCEPTANCE_OR_REVIEW', 1 FROM delivery_stage WHERE code = 'S5';
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S5_FINAL_REVIEW_REPORT', '终验专家评审报告签字版', true, 1, 2, NULL, 'S5_ACCEPTANCE_OR_REVIEW', 1 FROM delivery_stage WHERE code = 'S5';

-- ------------------------------------------------------------
-- 4. evidence_item：新增 stage_id、evidence_type_code；删除 status、biz_type
-- ------------------------------------------------------------
ALTER TABLE evidence_item ADD COLUMN IF NOT EXISTS stage_id BIGINT REFERENCES delivery_stage(id) ON DELETE RESTRICT;
ALTER TABLE evidence_item ADD COLUMN IF NOT EXISTS evidence_type_code VARCHAR(100);

COMMENT ON COLUMN evidence_item.stage_id IS '所属阶段 delivery_stage.id';
COMMENT ON COLUMN evidence_item.evidence_type_code IS '证据类型编码，与 stage_evidence_template 对应';

-- 历史数据回填（无数据时无影响）
UPDATE evidence_item ei
SET stage_id = (SELECT id FROM delivery_stage WHERE code = 'S1' LIMIT 1),
    evidence_type_code = 'OTHER'
WHERE ei.stage_id IS NULL;

ALTER TABLE evidence_item ALTER COLUMN stage_id SET NOT NULL;
ALTER TABLE evidence_item ALTER COLUMN evidence_type_code SET NOT NULL;

DROP INDEX IF EXISTS idx_evidence_item_status;
DROP INDEX IF EXISTS idx_evidence_item_project_id_biz_type;

ALTER TABLE evidence_item DROP COLUMN IF EXISTS status;
ALTER TABLE evidence_item DROP COLUMN IF EXISTS biz_type;

CREATE INDEX IF NOT EXISTS idx_evidence_item_stage_type ON evidence_item(project_id, stage_id, evidence_type_code);

-- ------------------------------------------------------------
-- 5. project：新增 has_procurement
-- ------------------------------------------------------------
ALTER TABLE project ADD COLUMN IF NOT EXISTS has_procurement BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN project.has_procurement IS '是否含采购；用于 S1 比测报告 required_when=HAS_PROCUREMENT 的参与计算判定';
