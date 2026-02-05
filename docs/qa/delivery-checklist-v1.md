# V1 清空与全流程验证 — 交付清单

本文为「稳健清空数据 + 超级管理员恢复 + V1 权限模型全流程人工测试」的**新增文件列表**与**一步一步执行说明**，便于按顺序完成清空与验证。

---

## 一、本次新增/涉及文件列表

| 类型     | 路径 | 说明 |
|----------|------|------|
| 清空脚本 | `db/scripts/reset_v1_safe.sql` | 仅清空业务表+用户表数据，不动 flyway_schema_history、不改表结构 |
| 恢复脚本 | `db/scripts/admin_recover.sql` | 清空后插入唯一超级管理员（admin / Admin@12345） |
| 可选脚本 | `db/scripts/seeds_auth_user_after_reset.sql` | C1 创建 5 个测试用户后执行，补齐 auth_user 供成员选择器使用 |
| 执行说明 | `docs/db/reset-v1-safe.md` | 备份、停服务、psql 命令、校验 SQL、超级管理员账号说明 |
| 人工测试 SOP | `docs/qa/v1-permission-manual-test.md` | V1 权限模型全流程人工测试步骤与预期（C0～C8） |
| 交付清单 | `docs/qa/delivery-checklist-v1.md` | 本文件 |

---

## 二、如何执行（一步不漏）

### 步骤 1：备份数据库（强烈建议）

```bash
pg_dump -h localhost -U <DB_USER> -d <DB_NAME> -F c -f backup_before_reset_$(date +%Y%m%d).dump
```

### 步骤 2：停掉后端服务

避免连接占用与写入冲突。

### 步骤 3：执行清空脚本

```bash
cd /path/to/evidence-manager
psql -h localhost -U <DB_USER> -d <DB_NAME> -f db/scripts/reset_v1_safe.sql
```

### 步骤 4：执行 admin 恢复脚本

```bash
psql -h localhost -U <DB_USER> -d <DB_NAME> -f db/scripts/admin_recover.sql
```

### 步骤 5：启动后端服务

按项目常规方式（如 `mvn spring-boot:run` 或运行 jar）。

### 步骤 6：确认 admin 可登录（C0）

- 打开登录页，**用户名：admin，密码：Admin@12345**
- 预期：登录成功，可见「用户管理」入口

### 步骤 7：按 SOP 执行 C1～C8 全流程验证

打开 **`docs/qa/v1-permission-manual-test.md`**，从 **C1** 起依次执行：

- **C1**：用 admin 在用户管理创建 5 个测试账号（pmo1、auditor1、u_owner、u_editor、u_viewer），并分配系统角色。
- **（可选）** 若成员管理「添加成员」时只看到 admin：执行  
  `psql ... -f db/scripts/seeds_auth_user_after_reset.sql`
- **C2**：admin 或 pmo1 创建项目 P-V1-001。
- **C3～C4**：成员管理中设置 u_owner 为负责人（并验证唯一 owner）、u_editor 为编辑、u_viewer 为查看。
- **C5**：分账号登录，验证可见范围与按钮级权限（见 SOP 表格）。
- **C6**：证据流转（owner 上传→提交→归档→作废）及 editor/viewer 权限边界。
- **C7**：越权必测（editor/viewer 调归档/作废接口预期 403）。
- **C8**：对照步骤与排查汇总表做最终核对。

### 步骤 8：（可选）执行前后校验 SQL

执行 reset 前、以及 reset + admin_recover 后，可运行 `docs/db/reset-v1-safe.md` 中的校验 SQL，确认业务表为 0、sys_user/auth_user 各 1（仅 admin）。

---

## 三、超级管理员登录信息（清空后）

| 项目   | 值            |
|--------|----------------|
| 用户名 | `admin`       |
| 密码   | `Admin@12345` |
| 角色   | SYSTEM_ADMIN  |

---

## 四、执行顺序小结

1. 备份 → 停服务  
2. **reset_v1_safe.sql** → **admin_recover.sql**  
3. 启动服务  
4. 登录 admin → C1 创建 5 用户 →（可选）**seeds_auth_user_after_reset.sql**  
5. 按 **v1-permission-manual-test.md** 完成 C2～C8  

按此清单即可完成清空与全流程验证。
