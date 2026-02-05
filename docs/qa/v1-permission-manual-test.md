# V1 权限模型 — 全流程人工测试 SOP

本文为「项目交付证据管理系统」V1 权限模型的全流程人工测试步骤，用于验证：可见范围、按钮级权限、接口与 permissions 同源、越权防护。

---

## C0. 前提：执行 reset + 确认 admin 可登录

1. 按 [docs/db/reset-v1-safe.md](../db/reset-v1-safe.md) 执行：
   - 备份 → 停服务 → 执行 `db/scripts/reset_v1_safe.sql` → 执行 `db/scripts/admin_recover.sql` → 启动服务
2. 打开登录页，使用 **用户名：admin，密码：Admin@12345** 登录。
3. **预期**：登录成功，进入首页；底部/「我的」中可见「用户管理」入口（仅 SYSTEM_ADMIN 可见）。
4. **失败排查**：查后端日志是否有 403/401；查 DB：`SELECT id, username, role_code, enabled FROM sys_user WHERE username='admin';` 与 `SELECT id, username FROM auth_user WHERE username='admin';` 应各有一条。

---

## C1. 用 admin 创建 5 个测试账号并分配系统角色

- **页面**：首页或「我的」→ 点击「用户管理」→ 进入用户管理列表。
- **操作**：点击「新增用户」，依次创建下表账号；角色在「角色」下拉中选择（**不使用 PROJECT_*，V1 已冻结**）。

| 登录账号 | 姓名/备注   | 角色（系统级） | 密码（建议统一便于测试） |
|----------|-------------|----------------|---------------------------|
| pmo1     | PMO测试     | **PMO（治理）** | Init@12345                |
| auditor1 | 审计只读    | **审计（只读入口）** | Init@12345                |
| u_owner  | 项目负责人  | 普通用户（如「项目查看」等，项目内权限在 C3/C4 成员管理中赋 owner） | Init@12345 |
| u_editor | 项目编辑    | 普通用户       | Init@12345                |
| u_viewer | 项目查看    | 普通用户       | Init@12345                |

**说明**：V1 项目内权限只看「项目 created_by + auth_project_acl」，与 sys_user.role_code 的 PROJECT_* 无关。用户管理里角色选 PMO/AUDITOR 或任选一普通角色即可；**项目内的 owner/editor/viewer 在 C3/C4 的「成员管理」中分配**。

- **操作要点**：每用户保存后，在列表中确认「角色」列显示正确；可编辑用户修改角色。
- **预期**：5 个用户创建成功，列表可见；admin 可编辑/启用/禁用/重置密码。
- **（可选）若项目成员选择器只显示 admin**：当前实现下用户管理仅写入 `sys_user`，成员选择器数据来自 `auth_user`。完成上述 5 个用户创建后，**手工执行** `db/scripts/seeds_auth_user_after_reset.sql`，可为这 5 个账号在 `auth_user` 中补齐记录，成员管理「添加成员」时即可选到 pmo1、auditor1、u_owner、u_editor、u_viewer。
  ```bash
  psql -h localhost -U <DB_USER> -d <DB_NAME> -f db/scripts/seeds_auth_user_after_reset.sql
  ```
- **失败排查**：看接口响应 message；后端 AdminUserService 校验 roleCode 是否在 VALID_ROLE_CODES 内；查 `sys_user`、若启用「用户同步 auth_user」则查 `auth_user` 是否有对应用户名。

---

## C2. admin 或 pmo1 创建项目

- **页面**：底部「项目」→ 项目列表 → 点击「新建项目」。
- **填写**：
  - 项目令号：`P-V1-001`
  - 项目名称：`V1 权限模型测试项目`
  - 项目描述：可选。
- **预期**：创建成功，进入项目详情或回到列表可见 P-V1-001；当前用户为该项目的 created_by，且 ACL 上会自动有一条当前用户为 owner（若实现如此）。
- **失败排查**：403 说明无权限（需 admin 或 pmo1）；400 多为令号重复或必填项为空。

---

## C3. 项目成员管理：分配项目经理（唯一 owner）

- **页面**：项目详情（P-V1-001）→ 底部「成员管理」按钮（需 canManageMembers，admin/pmo1 或项目负责人可见）。
- **操作 1**：点击「添加成员」→ 选择用户 `u_owner`，角色选「负责人」→ 确定。
- **预期**：成员列表中出现 u_owner，角色为「负责人」；项目详情「当前项目经理」显示 u_owner 的展示名。
- **操作 2（验证唯一 owner）**：再次「添加成员」或「编辑成员」→ 将 **u_editor** 设为「负责人」并保存。
- **预期**：列表中 u_editor 为负责人，u_owner 变为非负责人（或仍为 editor，取决于实现）；**最终该项目只有一名负责人**。若实现为「设新 owner 时删旧 owner」，则 u_owner 会从 owner 变为非 owner 或仅剩 u_editor 为 owner。
- **失败排查**：无「成员管理」入口时检查当前用户 roleCode 与项目 ACL；接口 403 看 PermissionUtil.checkCanManageMembers；查 DB：`SELECT * FROM auth_project_acl WHERE project_id=(SELECT id FROM project WHERE code='P-V1-001');` 确认 role=owner 仅一条。

---

## C4. 添加成员：u_editor = editor，u_viewer = viewer

- **页面**：同上，项目 P-V1-001 的成员管理。
- **操作**：添加 u_editor 角色「编辑」，添加 u_viewer 角色「查看」。若 u_owner/u_editor 已在列表中，用「编辑」修改角色即可。
- **预期**：列表中有 u_owner（负责人）、u_editor（编辑）、u_viewer（查看）；当前项目经理仍仅一人。
- **失败排查**：同 C3；角色下拉需包含 owner/editor/viewer。

---

## C5. 分账号验证「可见范围 + 按钮级权限」

以下均需**重新登录**对应用户后操作；每个账号验证完可汇总为一张表。

| 账号      | 角色/身份     | 预期：可见项目     | 预期：项目详情「上传证据」 | 预期：证据详情「提交/归档/作废」 | 预期：成员管理入口 | 预期：作废证据页入口 |
|-----------|---------------|--------------------|----------------------------|----------------------------------|--------------------|----------------------|
| admin     | SYSTEM_ADMIN  | 全部               | 可见                       | 可见                             | 可见               | 可见                 |
| pmo1      | PMO           | 全部               | **不可见**（除非在该项目为 owner/editor） | **不可见**（同上）       | 可见               | 不可见（仅 AUDITOR+admin） |
| auditor1  | AUDITOR       | 仅其 ACL/created_by 的项目 | 不可见                   | 不可见                           | 不可见             | **可见（只读）**，页上有「可查看不等于可作废/可操作」提示 |
| u_owner   | 项目 owner    | P-V1-001 等        | 可见                       | 可见                             | 可见               | 按实现（有 canInvalidate 则可见入口） |
| u_editor  | 项目 editor   | P-V1-001           | 可见                       | 仅「提交」可见，归档/作废不可见  | 不可见             | 不可见               |
| u_viewer   | 项目 viewer   | P-V1-001           | **不可见**                 | 不可见                           | 不可见             | 不可见               |

- **验证方式**：
  - 可见项目：登录后「项目」列表是否包含 P-V1-001。
  - 上传按钮：进入 P-V1-001 项目详情，看是否有「上传证据」按钮（V1 由 permissions.canUpload 控制）。
  - 提交/归档/作废：进入某条证据详情，看按钮是否与上表一致（permissions.canSubmit/canArchive/canInvalidate）。
  - 成员管理：项目详情页是否有「成员管理」按钮（permissions.canManageMembers）。
  - 作废证据页：底部或证据管理内是否有「作废证据」入口；auditor1 进入后应为只读列表 + 提示文案。
- **失败排查**：若按钮与预期不符，查接口返回的 `permissions` 或扁平 `canUpload`/`canInvalidate` 等；查后端 PermissionUtil.computeProjectPermissionBits 与 roleCode/ACL 是否一致。

---

## C6. 证据流转 + 权限边界

- **用 u_owner 登录**：
  1. 进入 P-V1-001 项目详情 → 点击「上传证据」→ 填写标题、选择文件 → 保存草稿。
  2. 进入该证据详情 → 点击「提交」→ 再点击「归档」。
  3. 点击「作废」→ 填写**作废原因**（必填）→ 确认。
  4. **预期**：状态依次 草稿 → 已提交 → 已归档 → 已作废；作废后可在「作废证据」页看到该条，且展示作废原因/人/时间（若有实现）。
- **用 u_editor 登录**：
  1. 进入同项目、任一条可提交的证据详情；**预期**：有「提交」按钮，**无「归档」「作废」**。
  2. 若用接口直接调归档/作废：**预期 403**（见 C7）。
- **用 u_viewer 登录**：
  1. 进入 P-V1-001 项目详情；**预期**：**无「上传证据」按钮**。
  2. 若用接口直接调上传：**预期 403**。

**失败排查**：状态流转失败看接口返回与 evidence_status 规则；作废原因为空应 400；403 看后端 checkCanArchive/checkCanInvalidate/checkCanUpload 日志。

---

## C7. 越权漏洞必测

### archiveEvidence（归档）

- **预期**：仅项目责任人（created_by 或 ACL owner）可成功；editor/viewer 调用应 **403**。
- **方法**：用 u_editor 或 u_viewer 登录，在浏览器拿到 Session Cookie 后，用 curl 调用归档接口（将 `EVIDENCE_ID`、`COOKIE` 替换为实际值）：

```bash
# 归档（editor/viewer 应 403）
curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:8080/api/evidence/EVIDENCE_ID/archive" \
  -H "Cookie: COOKIE"
```

- **预期**：返回 `403`。若为 200 则存在越权，需检查 EvidenceService.archiveEvidence 是否使用 checkCanArchive（与 checkCanInvalidate 同源）。

### invalidateEvidence（作废）

- **预期**：仅项目责任人可成功；必填 invalidReason；非责任人 **403**。
- **方法**：用 u_editor 登录，调用作废接口（同上替换 ID 与 Cookie）：

```bash
# 作废（editor 应 403）
curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:8080/api/evidence/EVIDENCE_ID/invalidate" \
  -H "Content-Type: application/json" \
  -H "Cookie: COOKIE" \
  -d '{"invalidReason":"测试"}'
```

- **预期**：返回 `403`。若为 200 则越权。另测责任人调用时缺 invalidReason 应 **400**。

**失败排查**：看后端日志 BusinessException 403/400；查 audit_log 是否有对应操作记录；确认 PermissionUtil.checkCanArchive/checkCanInvalidate 在接口内被调用。

---

## C8. 步骤与排查汇总

| 步骤 | 页面/入口           | 操作要点                     | 预期 UI/接口              | 失败时排查                           |
|------|---------------------|------------------------------|---------------------------|--------------------------------------|
| C0   | 登录页              | admin / Admin@12345          | 登录成功，见用户管理      | sys_user、auth_user 是否有 admin     |
| C1   | 用户管理            | 新增 5 用户，分配角色        | 列表可见，角色正确        | 接口 message、VALID_ROLE_CODES       |
| C2   | 项目 → 新建项目     | P-V1-001                     | 创建成功                  | 403 权限、400 令号重复                |
| C3   | 项目详情 → 成员管理 | 先 u_owner=负责人，再 u_editor=负责人 | 唯一负责人               | auth_project_acl 中 role=owner 仅 1 条 |
| C4   | 成员管理            | u_editor=编辑，u_viewer=查看 | 角色正确                  | 同 C3                                |
| C5   | 各账号登录          | 看项目列表、上传/提交/归档/作废/成员管理/作废证据入口 | 与 C5 表一致        | 接口 permissions、computeProjectPermissionBits |
| C6   | 证据详情/列表       | owner 流转；editor 无归档作废；viewer 无上传 | 按钮与接口一致     | evidence_status、checkCan*           |
| C7   | curl 或接口工具     | editor/viewer 调归档/作废    | 403                       | checkCanArchive/checkCanInvalidate   |

---

## 附录：接口与权限对应（便于抓包/curl）

- `GET /api/projects` — 可见项目列表（SYSTEM_ADMIN/PMO 全部，其余按 created_by + ACL）
- `GET /api/projects/{id}` — 项目详情，返回 `permissions`（canUpload/canInvalidate/canManageMembers 等）
- `GET /api/evidence/{id}` — 证据详情，返回 `permissions`
- `POST /api/projects/{id}/evidences` — 上传证据（需 canUpload）
- `POST /api/evidence/{id}/submit` — 提交（需 canSubmit）
- `POST /api/evidence/{id}/archive` — 归档（需 canArchive，仅责任人）
- `POST /api/evidence/{id}/invalidate` — 作废，body `{ "invalidReason": "必填" }`（需 canInvalidate，仅责任人）
- `GET /api/admin/users` — 用户管理列表（仅 SYSTEM_ADMIN，后端 AdminInterceptor 校验）

以上 SOP 覆盖 V1 权限模型的全流程与越权验证，可按步骤执行并对照预期结果与排查项完成人工测试。
