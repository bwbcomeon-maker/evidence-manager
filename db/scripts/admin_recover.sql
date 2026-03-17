-- ============================================================
-- 超级管理员恢复脚本（仅在执行 reset_v1_safe.sql 之后手工执行）
-- 当前版本仅恢复 sys_user 中的 admin 记录，不插入项目/证据等业务数据。
-- 执行顺序：reset_v1_safe.sql → admin_recover.sql → 启动服务
-- ============================================================

-- 依赖：pgcrypto 扩展（V4 已创建）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

\if :{?ADMIN_PASSWORD}
\else
\echo 'ERROR: 请使用 -v ADMIN_PASSWORD=<强密码> 传入管理员密码'
\quit 1
\endif

-- 当前系统已在 V11 删除 auth_user 表，业务用户统一使用 sys_user。
-- 恢复逻辑：
-- 1) 若 admin 不存在，则插入一条启用状态的 SYSTEM_ADMIN
-- 2) 若 admin 已存在，则强制恢复为 SYSTEM_ADMIN + 启用 + 未删除，并更新为传入密码
INSERT INTO sys_user (username, password_hash, real_name, role_code, enabled, is_deleted)
VALUES (
  'admin',
  crypt(:'ADMIN_PASSWORD', gen_salt('bf', 10)),
  '系统管理员',
  'SYSTEM_ADMIN',
  true,
  false
)
ON CONFLICT (username) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  real_name = EXCLUDED.real_name,
  role_code = 'SYSTEM_ADMIN',
  enabled = true,
  is_deleted = false,
  updated_at = now();
