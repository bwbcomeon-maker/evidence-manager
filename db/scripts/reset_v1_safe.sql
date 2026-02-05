-- ============================================================
-- 稳健清空数据脚本（V1 安全重置）
-- 约束：不修改 flyway_schema_history，不改表结构，仅清空业务与用户数据。
-- 执行方式：手工执行，不作为 Flyway migration，不放入 db/migration。
-- ============================================================

-- 将被清空的表清单（与仓库 migration/实体 一致）：
--   evidence_version  (V2) - 证据版本
--   evidence_item     (V1) - 证据元数据
--   auth_project_acl  (V1) - 项目 ACL
--   project           (V1) - 项目
--   audit_operation_log (V1) - 操作审计日志
--   audit_log         (V4) - 登录/操作审计
--   sys_user          (V4) - 系统用户（登录账号）
--   auth_user         (V1) - 业务用户（UUID，项目/证据关联）
-- 明确：不清 flyway_schema_history。

-- 清空顺序：子表/依赖先，父表后（避免 FK 报错）
-- 使用 RESTART IDENTITY 重置自增主键，CASCADE 级联清空依赖本表的外键表（本库内无额外依赖表）
TRUNCATE TABLE evidence_version RESTART IDENTITY CASCADE;
TRUNCATE TABLE evidence_item    RESTART IDENTITY CASCADE;
TRUNCATE TABLE auth_project_acl RESTART IDENTITY CASCADE;
TRUNCATE TABLE project          RESTART IDENTITY CASCADE;
TRUNCATE TABLE audit_operation_log RESTART IDENTITY CASCADE;
TRUNCATE TABLE audit_log        RESTART IDENTITY CASCADE;
TRUNCATE TABLE sys_user         RESTART IDENTITY CASCADE;
TRUNCATE TABLE auth_user        RESTART IDENTITY CASCADE;

-- 可选表：仅当存在时清空，避免脚本在未建表环境报错
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'project_init_batch') THEN
    EXECUTE 'TRUNCATE TABLE project_init_batch RESTART IDENTITY CASCADE';
  END IF;
END $$;
