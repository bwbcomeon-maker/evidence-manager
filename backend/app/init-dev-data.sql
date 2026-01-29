-- 开发/联调用：插入测试用户与项目 id=1，使前端「项目详情 / 证据上传」可正常请求后端
-- 执行一次即可（重复执行会因 ON CONFLICT 而跳过或报错，可忽略）

-- 1. 固定测试用户（与 Controller 中 currentUserId 一致）
INSERT INTO auth_user (id, username, display_name, email, is_active)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'devuser',
  '开发测试用户',
  'dev@localhost',
  true
)
ON CONFLICT (id) DO NOTHING;

-- 2. 项目 id=1（若已存在则跳过）
INSERT INTO project (id, code, name, description, status, created_by, created_at, updated_at)
VALUES (1, 'PROJ-001', '智慧城市项目', '智慧城市综合管理平台开发项目', 'active', '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
