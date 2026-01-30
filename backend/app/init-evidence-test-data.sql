-- ============================================================
-- 证据管理模块联调：最小假数据（5 角色 + 2 项目 + 3 类文件 + 1 条作废）
-- 执行前请确保 V1/V2/V3/V4 已跑完；重复执行会 ON CONFLICT 跳过。
-- 若 auth_user 中已存在 admin/owner1/editor1/viewer1/auditor1，本脚本会跳过插入，
-- 此时 evidence_item 的 created_by 使用固定 UUID，可能造成外键失败，请仅首次在无此 5 个 username 时执行。
-- ============================================================

-- 1. auth_user：与 sys_user 用户名一一对应（admin 已在 V4 的 sys_user 中，此处补 auth_user）
INSERT INTO auth_user (id, username, display_name, email, is_active)
VALUES
  ('a0000000-0000-0000-0000-000000000001', 'admin',    '系统管理员',   'admin@test',   true),
  ('b0000000-0000-0000-0000-000000000001', 'owner1',   '项目负责人',   'owner@test',   true),
  ('c0000000-0000-0000-0000-000000000001', 'editor1',  '项目编辑',     'editor@test',  true),
  ('d0000000-0000-0000-0000-000000000001', 'viewer1',  '项目查看',     'viewer@test',  true),
  ('e0000000-0000-0000-0000-000000000001', 'auditor1', '项目审计',     'auditor@test', true)
ON CONFLICT (username) DO NOTHING;

-- 2. sys_user：5 角色（admin 已由 V4 插入，此处补 4 个；密码统一 Test@12345）
INSERT INTO sys_user (username, password_hash, real_name, role_code, enabled, is_deleted)
VALUES
  ('owner1',  crypt('Test@12345', gen_salt('bf', 10)), '项目负责人', 'PROJECT_OWNER',  true, false),
  ('editor1', crypt('Test@12345', gen_salt('bf', 10)), '项目编辑',   'PROJECT_EDITOR', true, false),
  ('viewer1', crypt('Test@12345', gen_salt('bf', 10)), '项目查看',   'PROJECT_VIEWER', true, false),
  ('auditor1', crypt('Test@12345', gen_salt('bf', 10)), '项目审计',   'PROJECT_AUDITOR', true, false)
ON CONFLICT (username) DO NOTHING;

-- 3. 项目 2（项目 1 已由 init-dev-data 创建，created_by=devuser）
INSERT INTO project (id, code, name, description, status, created_by, created_at, updated_at)
VALUES (
  2,
  'PROJ-002',
  '证据联调测试项目',
  '用于证据管理模块联调自测',
  'active',
  'b0000000-0000-0000-0000-000000000001',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- 4. auth_project_acl：让 owner1/editor1/viewer1/auditor1 能看见项目 1 和 2
INSERT INTO auth_project_acl (project_id, user_id, role)
VALUES
  (1, 'b0000000-0000-0000-0000-000000000001', 'owner'),
  (1, 'c0000000-0000-0000-0000-000000000001', 'editor'),
  (1, 'd0000000-0000-0000-0000-000000000001', 'viewer'),
  (1, 'e0000000-0000-0000-0000-000000000001', 'viewer'),
  (2, 'c0000000-0000-0000-0000-000000000001', 'editor'),
  (2, 'd0000000-0000-0000-0000-000000000001', 'viewer'),
  (2, 'e0000000-0000-0000-0000-000000000001', 'viewer')
ON CONFLICT (project_id, user_id) DO NOTHING;

-- 5. evidence_item：项目 1 四条（image/document/video/作废），项目 2 三条（image/document/video）
--    evidence_status 生命周期：DRAFT/SUBMITTED/ARCHIVED/INVALID（需先执行 V5__evidence_status_lifecycle.sql）
INSERT INTO evidence_item (
  id, project_id, title, note, bucket, object_key, content_type, size_bytes,
  status, evidence_status, archived_time, invalid_time, biz_type, created_by, created_at, updated_at
)
VALUES
  (101, 1, '项目1-图片证据', NULL, 'default', '1/101/sample.png',  'image/png',  1024, 'active',  'SUBMITTED', NULL, NULL, 'OTHER', 'a0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP),
  (102, 1, '项目1-文档证据', NULL, 'default', '1/102/sample.pdf',  'application/pdf', 2048, 'archived', 'ARCHIVED', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL, 'OTHER', 'b0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP),
  (103, 1, '项目1-视频证据', NULL, 'default', '1/103/sample.mp4',  'video/mp4',  4096, 'active',  'SUBMITTED', NULL, NULL, 'OTHER', 'c0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '1 day',  CURRENT_TIMESTAMP),
  (104, 1, '项目1-作废证据', NULL, 'default', '1/104/voided.jpg',  'image/jpeg', 512,  'invalid', 'INVALID', NULL, CURRENT_TIMESTAMP - INTERVAL '5 days', 'OTHER', 'a0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP),
  (105, 2, '项目2-图片证据', NULL, 'default', '2/105/sample.png',  'image/png',  1024, 'active',  'DRAFT', NULL, NULL, 'OTHER', 'b0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP),
  (106, 2, '项目2-文档证据', NULL, 'default', '2/106/sample.pdf',  'application/pdf', 2048, 'active',  'SUBMITTED', NULL, NULL, 'OTHER', 'b0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP),
  (107, 2, '项目2-视频证据', NULL, 'default', '2/107/sample.mp4',  'video/mp4',  4096, 'active',  'SUBMITTED', NULL, NULL, 'OTHER', 'c0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 6. 同步 evidence_item 主键序列（若上面使用了固定 id）
SELECT setval(pg_get_serial_sequence('evidence_item', 'id'), (SELECT COALESCE(MAX(id), 1) FROM evidence_item));

-- 7. evidence_version：每条证据对应一个版本（下载/预览依赖版本表）
INSERT INTO evidence_version (
  evidence_id, project_id, version_no, original_filename, file_path, file_size, content_type, uploader_id, created_at
)
VALUES
  (101, 1, 1, 'sample.png', '1/101/sample.png',  1024, 'image/png',  'a0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '2 days'),
  (102, 1, 1, 'sample.pdf', '1/102/sample.pdf',  2048, 'application/pdf', 'b0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '3 days'),
  (103, 1, 1, 'sample.mp4', '1/103/sample.mp4',  4096, 'video/mp4',  'c0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '1 day'),
  (104, 1, 1, 'voided.jpg', '1/104/voided.jpg',  512,  'image/jpeg', 'a0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '5 days'),
  (105, 2, 1, 'sample.png', '2/105/sample.png',  1024, 'image/png',  'b0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '4 days'),
  (106, 2, 1, 'sample.pdf', '2/106/sample.pdf',  2048, 'application/pdf', 'b0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '6 days'),
  (107, 2, 1, 'sample.mp4', '2/107/sample.mp4',  4096, 'video/mp4',  'c0000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP - INTERVAL '2 days')
ON CONFLICT (evidence_id, version_no) DO NOTHING;
