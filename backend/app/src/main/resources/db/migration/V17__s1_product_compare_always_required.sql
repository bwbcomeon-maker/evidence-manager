-- 项目启动阶段「项目前期产品比测报告」改为始终必填（不再依赖「是否含采购」）
UPDATE stage_evidence_template
SET required_when = NULL
WHERE evidence_type_code = 'S1_PRODUCT_COMPARE';
