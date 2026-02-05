# 稳健清空数据（V1 安全重置）— 执行说明

本文说明如何执行 `reset_v1_safe.sql` 与 `admin_recover.sql`，以及超级管理员登录信息。**不修改 flyway_schema_history，不改表结构。**

---

## 一、执行前准备

### 1. 备份（强烈建议）

```bash
# 按需替换 <DB_NAME>、<DB_USER>、<BACKUP_FILE>
pg_dump -h localhost -U <DB_USER> -d <DB_NAME> -F c -f <BACKUP_FILE>.dump
# 示例
pg_dump -h localhost -U postgres -d evidence_db -F c -f backup_before_reset_$(date +%Y%m%d).dump
```

### 2. 执行前后校验 SQL（可选但建议执行）

**执行 reset 前**（记录数量，便于对比）：

```sql
SELECT 'project' AS tbl, count(*) FROM project
UNION ALL SELECT 'evidence_item', count(*) FROM evidence_item
UNION ALL SELECT 'evidence_version', count(*) FROM evidence_version
UNION ALL SELECT 'auth_project_acl', count(*) FROM auth_project_acl
UNION ALL SELECT 'sys_user', count(*) FROM sys_user
UNION ALL SELECT 'auth_user', count(*) FROM auth_user
UNION ALL SELECT 'audit_log', count(*) FROM audit_log;
```

**执行 reset + admin_recover 后**（预期：业务表为 0，sys_user/auth_user 各 1）：

```sql
SELECT 'project' AS tbl, count(*) FROM project
UNION ALL SELECT 'evidence_item', count(*) FROM evidence_item
UNION ALL SELECT 'evidence_version', count(*) FROM evidence_version
UNION ALL SELECT 'auth_project_acl', count(*) FROM auth_project_acl
UNION ALL SELECT 'sys_user', count(*) FROM sys_user
UNION ALL SELECT 'auth_user', count(*) FROM auth_user
UNION ALL SELECT 'audit_log', count(*) FROM audit_log;
```

预期结果：`project` / `evidence_item` / `evidence_version` / `auth_project_acl` / `audit_log` 均为 **0**；`sys_user` 与 `auth_user` 均为 **1**（仅 admin）。

---

## 二、执行顺序（必须按此顺序）

1. **停掉后端服务**（避免连接占用与写入）
2. **执行清空脚本** `reset_v1_safe.sql`
3. **执行恢复脚本** `admin_recover.sql`
4. **启动后端服务**

### 执行清空脚本（psql）

```bash
# 进入项目根目录（脚本路径相对于仓库根）
cd /path/to/evidence-manager

# 执行 reset（按需改 -h / -U / -d）
psql -h localhost -U <DB_USER> -d <DB_NAME> -f db/scripts/reset_v1_safe.sql
```

### 执行 admin 恢复脚本

```bash
psql -h localhost -U <DB_USER> -d <DB_NAME> -f db/scripts/admin_recover.sql
```

### 启动服务

按项目常规方式启动后端（如 `mvn spring-boot:run` 或运行 jar）。

---

## 三、超级管理员登录信息（清空后唯一可登录账号）

执行 `admin_recover.sql` 后，以下账号用于在页面创建测试用户与分配角色：

| 项目     | 值            |
|----------|---------------|
| **用户名** | `admin`       |
| **密码**   | `Admin@12345` |
| **角色**   | SYSTEM_ADMIN  |

- 来源：V4 迁移中初始 admin 的密码规则为 `crypt('Admin@12345', gen_salt('bf', 10))`；`admin_recover.sql` 与之一致，保证清空后仍可登录。
- 若需修改密码，可在用户管理（admin 登录后）自行修改，或改 `admin_recover.sql` 中 `crypt('...', ...)` 后重新执行该脚本（注意会与现有 admin 冲突时使用 ON CONFLICT DO NOTHING，仅首次执行会插入）。

---

## 四、脚本说明摘要

| 脚本                    | 作用                         | 是否动 flyway_schema_history | 是否改表结构 |
|-------------------------|------------------------------|------------------------------|--------------|
| `db/scripts/reset_v1_safe.sql`   | 清空业务表 + 用户表数据       | 否                           | 否           |
| `db/scripts/admin_recover.sql`   | 仅插入 admin（auth_user + sys_user） | 否                   | 否           |

清空表清单（与仓库 migration 一致）：`evidence_version` → `evidence_item` → `auth_project_acl` → `project` → `audit_operation_log` → `audit_log` → `sys_user` → `auth_user`；可选表 `project_init_batch` 存在则清空。

**可选脚本**（仅用于 V1 人工测试流程）：`db/scripts/seeds_auth_user_after_reset.sql`。在完成「用户管理」中创建 pmo1/auditor1/u_owner/u_editor/u_viewer 之后执行，可为上述账号在 `auth_user` 中补齐记录，便于项目成员选择器能选到这些用户。执行顺序：reset → admin_recover → 启动服务 → 登录 admin 创建 5 用户 →（可选）执行 seeds_auth_user_after_reset.sql。
