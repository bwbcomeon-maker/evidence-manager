-- ============================================================
-- 开发环境业务数据清空脚本（仅开发使用）
-- 约束：不修改 flyway_schema_history，不清 sys_user（保留登录账号）。
-- 执行顺序：先执行本脚本，再启动应用或执行 Flyway，使 V13 等迁移可安全做 ALTER。
-- ============================================================

-- 清空顺序：子表/依赖先，父表后（避免 FK 报错）
-- 使用 RESTART IDENTITY 重置自增主键；CASCADE 级联清空依赖本表的外键表

TRUNCATE TABLE evidence_version       RESTART IDENTITY CASCADE;
TRUNCATE TABLE evidence_item          RESTART IDENTITY CASCADE;
TRUNCATE TABLE audit_operation_log    RESTART IDENTITY CASCADE;
TRUNCATE TABLE audit_log              RESTART IDENTITY CASCADE;
TRUNCATE TABLE auth_project_acl       RESTART IDENTITY CASCADE;

-- project_stage 在 V13 中创建，仅当存在时清空
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'project_stage') THEN
    TRUNCATE TABLE project_stage RESTART IDENTITY CASCADE;
  END IF;
END $$;

TRUNCATE TABLE project                RESTART IDENTITY CASCADE;

-- 不清空：flyway_schema_history, sys_user
