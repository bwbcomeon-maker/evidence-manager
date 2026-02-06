-- ============================================================
-- Break-glass：恢复 admin 账号（仅开发/测试环境）
-- 场景：SYSTEM_ADMIN 在用户管理里误禁用自己导致无法登录时，由 DBA/运维在数据库执行本脚本。
-- 不改 flyway_schema_history，不改表结构。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. sys_user：将 username='admin' 置为启用、未删除、角色为 SYSTEM_ADMIN
--    若记录存在则只更新状态；若不存在则插入（与 admin_recover.sql 一致）
INSERT INTO sys_user (username, password_hash, real_name, role_code, enabled, is_deleted)
VALUES (
  'admin',
  crypt('Admin@12345', gen_salt('bf', 10)),
  '系统管理员',
  'SYSTEM_ADMIN',
  true,
  false
)
ON CONFLICT (username) DO UPDATE SET
  enabled   = true,
  is_deleted = false,
  role_code = 'SYSTEM_ADMIN';

-- 2. auth_user：若缺失则补齐（供项目成员选择器等使用）
INSERT INTO auth_user (id, username, display_name, email, is_active)
VALUES (
  'a0000000-0000-0000-0000-000000000001',
  'admin',
  '系统管理员',
  'admin@test',
  true
)
ON CONFLICT (username) DO NOTHING;

-- 执行后请用 admin / Admin@12345 登录验证。
