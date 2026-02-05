-- V1 权限定稿：PROJECT_AUDITOR 迁移为 AUDITOR（作废证据/审计入口统一为系统级 AUDITOR）
UPDATE sys_user SET role_code = 'AUDITOR' WHERE role_code = 'PROJECT_AUDITOR';
