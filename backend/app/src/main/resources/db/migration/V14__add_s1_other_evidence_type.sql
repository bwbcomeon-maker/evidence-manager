-- ============================================================
-- 项目启动阶段增加「其他」证据类型，便于上传界面展示并归类
-- ============================================================

-- 1. 新增 S1 阶段「其他」模板项（选填，不参与门禁）
INSERT INTO stage_evidence_template (stage_id, evidence_type_code, display_name, is_required, min_count, sort_order, required_when, rule_group, group_required_count)
SELECT id, 'S1_OTHER', '其他', false, 0, 10, NULL, NULL, NULL
FROM delivery_stage WHERE code = 'S1'
ON CONFLICT (stage_id, evidence_type_code) DO NOTHING;

-- 2. 将 S1 下已存在的 evidence_type_code='OTHER' 统一为 S1_OTHER，便于归入「其他」展示
UPDATE evidence_item ei
SET evidence_type_code = 'S1_OTHER'
WHERE ei.stage_id = (SELECT id FROM delivery_stage WHERE code = 'S1' LIMIT 1)
  AND ei.evidence_type_code = 'OTHER';
