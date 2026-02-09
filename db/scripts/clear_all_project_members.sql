-- 清空所有项目的项目成员（仅删除 auth_project_acl 表数据，不影响 project 表）
-- 执行前请确认：执行后所有项目在「项目成员」中均显示为空，需在成员管理中重新分配。
-- 用法：在 PostgreSQL 中执行本脚本，例如 psql -U evidence -d evidence -f clear_all_project_members.sql

DELETE FROM auth_project_acl;
