# 超级管理员（admin）恢复说明

本文说明两种恢复 admin 可登录能力的场景及执行方式。**不修改 flyway_schema_history，不修改表结构。**

---

## 一、执行 reset 后的恢复（常规）

在按 [reset-v1-safe.md](./reset-v1-safe.md) 执行 `reset_v1_safe.sql` 清空数据后，需执行 **`db/scripts/admin_recover.sql`** 恢复 `admin`，才能登录。

**执行顺序**：停服务 → `reset_v1_safe.sql` → **`admin_recover.sql`** → 启动服务。

**命令示例**：

```bash
psql -h localhost -U <DB_USER> -d <DB_NAME> -v ADMIN_PASSWORD='<强密码>' -f db/scripts/admin_recover.sql
```

**恢复后登录**：用户名 `admin`，密码为执行脚本时传入的 `ADMIN_PASSWORD`。

---

## 二、Break-glass：admin 误禁用自己后的恢复（仅开发/测试）

**场景**：SYSTEM_ADMIN 在「用户管理」里误操作将自己禁用（或误删），导致无法登录。生产环境应通过流程与权限避免；开发/测试环境可通过数据库脚本自救。

**脚本**：**`db/scripts/admin_recover_dev.sql`**（独立手工脚本，不作为 Flyway migration，不放入 `db/migration`）。

**作用**：

- 将 `sys_user` 中 `username='admin'` 的记录置为：`enabled = true`、`is_deleted = false`、`role_code = 'SYSTEM_ADMIN'`。
- 若 admin 在 `sys_user` 中不存在（例如被逻辑删除后又被物理清理），则插入一条 admin 记录，密码取自执行脚本时传入的 `ADMIN_PASSWORD`。
- 当前系统已删除 `auth_user` 表，因此恢复脚本仅操作 `sys_user`。

**执行方式**：

1. 停掉后端服务（建议，避免会话冲突）。
2. 在项目根目录执行：

```bash
psql -h localhost -U <DB_USER> -d <DB_NAME> -v ADMIN_PASSWORD='<强密码>' -f db/scripts/admin_recover_dev.sql
```

3. 启动后端服务。
4. 使用 **admin / 你传入的 ADMIN_PASSWORD** 登录验证。

**注意**：

- 本脚本仅用于**开发/测试环境**自救，文档化但**默认不暴露在 UI**。
- 生产环境应严格限制数据库直连与脚本执行权限，并依赖「禁止自我操作」的后端与前端防护，避免 admin 误禁自己。

---

## 三、校验

执行任一恢复脚本后，可执行：

```sql
SELECT id, username, enabled, is_deleted, role_code FROM sys_user WHERE username = 'admin';
```

预期：`enabled = true`，`is_deleted = false`，`role_code = 'SYSTEM_ADMIN'`。
