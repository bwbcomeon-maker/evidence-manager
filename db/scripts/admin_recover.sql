-- ============================================================
-- 超级管理员恢复脚本（仅在执行 reset_v1_safe.sql 之后手工执行）
-- 仅插入 sys_user + auth_user 的 admin 记录，不插入项目/证据等业务数据。
-- 执行顺序：reset_v1_safe.sql → admin_recover.sql → 启动服务
-- ============================================================

-- 依赖：pgcrypto 扩展（V4 已创建）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. auth_user：业务侧用户（UUID），与 sys_user.username 对应，供项目/证据 created_by 等使用
INSERT INTO auth_user (id, username, display_name, email, is_active)
VALUES (
  'a0000000-0000-0000-0000-000000000001',
  'admin',
  '系统管理员',
  'admin@test',
  true
)
ON CONFLICT (username) DO NOTHING;

-- 2. sys_user：登录账号，role_code=SYSTEM_ADMIN，密码 Admin@12345
INSERT INTO sys_user (username, password_hash, real_name, role_code, enabled, is_deleted)
VALUES (
  'admin',
  crypt('Admin@12345', gen_salt('bf', 10)),
  '系统管理员',
  'SYSTEM_ADMIN',
  true,
  false
)
ON CONFLICT (username) DO NOTHING;
