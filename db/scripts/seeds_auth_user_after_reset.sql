-- ============================================================
-- 可选：为「已在用户管理创建的登录账号」补齐 auth_user（便于项目成员选择器能选到）
-- 执行时机：完成 C1 在页面创建 pmo1/auditor1/u_owner/u_editor/u_viewer 之后执行。
-- 仅插入 auth_user，不插入项目/证据等业务数据；不改表结构。
-- ============================================================

INSERT INTO auth_user (id, username, display_name, email, is_active)
VALUES
  (gen_random_uuid(), 'pmo1',     'PMO测试',   'pmo1@test',    true),
  (gen_random_uuid(), 'auditor1', '审计只读',  'auditor1@test', true),
  (gen_random_uuid(), 'u_owner',  '项目负责人', 'u_owner@test',  true),
  (gen_random_uuid(), 'u_editor', '项目编辑',   'u_editor@test', true),
  (gen_random_uuid(), 'u_viewer', '项目查看',   'u_viewer@test', true)
ON CONFLICT (username) DO NOTHING;
