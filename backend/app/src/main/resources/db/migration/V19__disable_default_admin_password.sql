-- 禁用仍使用已知默认口令的 admin，避免固定口令继续流入正式环境。
-- 如需恢复/初始化管理员，请使用安全引导配置 security.bootstrap-admin.*。

UPDATE sys_user
SET enabled = false,
    updated_at = now()
WHERE username = 'admin'
  AND role_code = 'SYSTEM_ADMIN'
  AND (is_deleted = false OR is_deleted IS NULL)
  AND password_hash = crypt('Admin@12345', password_hash);
