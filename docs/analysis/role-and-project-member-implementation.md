# 角色管理与项目成员实现分析

## 一、概念区分

| 概念 | 存储位置 | 取值 | 含义 |
|------|----------|------|------|
| **系统级角色** | `sys_user.role_code` | SYSTEM_ADMIN / PMO / AUDITOR / USER | 全局身份：谁能进管理后台、谁看全部项目、谁只能看作废证据等 |
| **项目内角色** | `auth_project_acl.role` | owner / editor / viewer | 在**某个项目**内：谁是项目经理、谁可编辑、谁只读 |

二者独立：系统角色在「用户管理」里分配；项目内角色在「项目 → 成员管理」里分配。同一用户在不同项目可以是不同角色（A 项目 owner，B 项目 viewer）。

---

## 二、用户创建（谁可以创建、写到哪里）

### 2.1 入口与权限

- **接口**：`POST /api/admin/users`（请求体：username, password?, realName, phone, email, **roleCode**, enabled）
- **权限**：仅 **SYSTEM_ADMIN** 可访问 `/api/admin/**`（由 `AdminInterceptor` 校验 `user.getRoleCode() == "SYSTEM_ADMIN"`）。
- **实现**：`AdminUserController` → `AdminUserService.create()`。

### 2.2 创建时写什么

- **只写一张表**：`sys_user`。
  - 字段：username, password_hash, real_name, phone, email, **role_code**, enabled, is_deleted 等。
  - `roleCode` 必填，且必须在 `AdminUserService.VALID_ROLE_CODES` 内：**SYSTEM_ADMIN / PMO / AUDITOR / USER**。
- **不写**：不写 `auth_project_acl`。即创建用户时**不会**自动成为任何项目的项目经理或成员。
- **约束**：禁止对「自己」执行创建以外的管理操作（禁用/改角色/删等）；admin 不能改 admin 自己。

### 2.3 小结

- **用户创建**：仅系统管理员在「用户管理」里创建，写入 `sys_user` 并指定**系统级角色**（PMO/审计/普通用户等）。
- **项目经理 / 项目成员**：不是通过“创建用户”直接产生的，而是通过**项目成员管理**或**批量分配接口**，在 `auth_project_acl` 里写入「谁在哪个项目是什么角色」。

---

## 三、项目经理与项目成员的权限分配（写到哪里、谁可以操作）

### 3.1 数据来源

- **项目创建人**：`project.created_by_user_id`（创建项目时由 `ProjectService.createProject(userId, ...)` 写入，并**同时**插入一条 `auth_project_acl`：该项目 + 该用户 + role=**owner**）。即创建人自动成为该项目项目经理（owner）。
- **其他项目经理 / 成员**：全部来自 **auth_project_acl**。
  - 表结构：`project_id`, `sys_user_id`, **role**（owner / editor / viewer）。
  - 约束：`(project_id, sys_user_id)` 唯一；每项目**最多一个** role=owner（分配新 owner 时会先删掉原 owner 再插入）。

### 3.2 谁可以“分配”项目经理 / 项目成员

- **管理成员权限**（`PermissionUtil.checkCanManageMembers`）满足其一即可：
  1. 当前用户 **sys_user.role_code** = SYSTEM_ADMIN；
  2. 当前用户 **sys_user.role_code** = PMO；
  3. 当前用户是该项目的**创建人**（project.created_by_user_id）；
  4. 当前用户在该项目的 **auth_project_acl.role** = **owner**。
- **接口**：
  - 单条：`POST /api/projects/{projectId}/members`，body：`{ userId, role: "owner"|"editor"|"viewer" }`。
  - 批量对多项目分配同一人：`POST /api/projects/batch-members`，body：`{ userId, projectIds, role }`（仅 PMO/SYSTEM_ADMIN）。
  - 对一项目批量加多人：`POST /api/projects/{projectId}/members/batch`，body：`{ members: [{ userId, role }, ...] }`。

### 3.3 分配时的校验与规则

- `userId` 必须存在（sys_user 中有该 id）。
- 不能“修改自己”（operator 不能把自己加入/改角色）。
- **role** 只能是 owner / editor / viewer。
- 若 **role = owner**：先删除该项目下其他所有人的 owner 记录，再插入/更新该用户为 owner（保证每项目一个项目经理）。

### 3.4 用户列表从哪里来（成员选择器）

- **接口**：`GET /api/users`。
- **实现**：`UserController.listUsers()` → `SysUserMapper.selectAll()`，返回所有 **sys_user** 的 id、username、realName。
- **用途**：前端在「添加项目成员」时下拉选人，选中的是 sys_user.id；提交时把该 id 作为 userId 与 role 一起传给 `POST .../members` 或 batch 接口。

---

## 四、权限如何被使用（可见项目、上传/归档/作废、管理成员）

### 4.1 可见项目范围（getVisibleProjectIds）

- **SYSTEM_ADMIN / PMO**：可见**全部**项目（直接查全部 project）。
- **其他用户**：可见 = **自己创建的项目**（project.created_by_user_id = 当前用户）∪ **自己在 ACL 里的项目**（auth_project_acl.sys_user_id = 当前用户）。

列表/详情/证据列表等都会先通过 `getVisibleProjectIds` 过滤，看不到的项目 403。

### 4.2 项目内权限位（PermissionUtil.computeProjectPermissionBits）

- **SYSTEM_ADMIN**：canUpload / canSubmit / canArchive / canInvalidate / canManageMembers 全为 true。
- **AUDITOR**：全为 false（仅能看作废证据入口的只读列表等）。
- **PMO**：
  - canManageMembers 恒为 true；
  - 其余（上传/提交/归档/作废）按**项目内角色**：若当前用户是该项目的 created_by 或 ACL 的 owner/editor 则有上传/提交，仅 owner 有归档/作废。
- **USER（及其他）**：
  - 项目内角色 = 若为项目创建人则视为 owner，否则取 auth_project_acl.role（无则无权限）。
  - canManageMembers = 仅 owner；
  - canUpload/canSubmit = owner 或 editor；
  - canArchive/canInvalidate = 仅 owner。

### 4.3 接口级校验

- 上传/提交证据：`checkCanUpload` / `checkCanSubmit`（SYSTEM_ADMIN 放行；否则项目内须 owner 或 editor）。
- 归档/作废证据：`checkCanArchive` / `checkCanInvalidate`（SYSTEM_ADMIN 或项目创建人或 ACL owner）。
- 管理成员：`checkCanManageMembers`（SYSTEM_ADMIN / PMO / 项目创建人 / 该项目 ACL owner）。
- 移除成员：同上；且不允许移除最后一个 owner，项目创建人若在 ACL 为唯一 owner 时也不能被移除。

---

## 五、流程串联（项目经理 / 项目成员从创建到有权限）

1. **系统管理员**登录 → 进入「用户管理」→ **创建用户**（填写账号、姓名、**系统角色**选 PMO/审计/普通用户等）→ 仅写入 **sys_user**，不涉及项目。
2. **创建项目**：任意有权限用户（如 PMO 或普通用户）调用 `POST /api/projects` → 写入 **project**（created_by_user_id = 当前用户）并插入一条 **auth_project_acl**（该项目、当前用户、role=owner）→ 该用户自动成为该项目**项目经理**。
3. **添加项目成员/项目经理**：
   - 由**项目责任人**（创建人或 ACL owner）或 **PMO/系统管理员** 在项目详情 → 成员管理 → 选择用户（来自 `GET /api/users`）+ 选择角色（owner/editor/viewer）→ 调用 `POST /api/projects/{id}/members` 或 batch → 写入/更新 **auth_project_acl**。
   - 若选 role=owner，即指定该用户为该项目的**项目经理**（每项目一个，会顶掉原 owner）。
4. **批量分配**：PMO/系统管理员可调用 `POST /api/projects/batch-members` 把**同一人**批量加入多个项目并指定角色；或调用 `POST /api/projects/{id}/members/batch` 为**一个项目**批量添加多人（含项目经理）。
5. **登录后**：Session 存当前用户 id，请求里带出 **sys_user.role_code** 与 id；可见项目、项目内权限位、上传/归档/作废/管理成员等均按上述规则计算。

---

## 六、小结表

| 环节 | 谁操作 | 写什么 | 关键接口/表 |
|------|--------|--------|-------------|
| 用户创建 | 仅 SYSTEM_ADMIN | sys_user（含 role_code） | POST /api/admin/users，AdminUserService.create |
| 项目创建 | 登录用户 | project.created_by_user_id + auth_project_acl(owner) | POST /api/projects，ProjectService.createProject |
| 指定项目经理/成员 | 项目责任人 或 PMO/SYSTEM_ADMIN | auth_project_acl（project_id, sys_user_id, role） | POST .../members、batch-members、.../members/batch |
| 成员选择器数据 | 登录用户 | 只读 | GET /api/users → sys_user 列表 |
| 可见项目 | - | 只读 | getVisibleProjectIds(roleCode, userId) → project + auth_project_acl |
| 项目内权限 | - | 只读 | computeProjectPermissionBits(projectId, userId, roleCode) + project / auth_project_acl |

项目经理 = 项目创建人（自动 owner）或 被在 auth_project_acl 中设为 role=owner 的用户；项目成员 = 在 auth_project_acl 中有记录且 role 为 editor/viewer（或 owner）的用户。用户必须先由管理员在 sys_user 中创建，再在项目成员管理中或通过批量接口被“分配”到项目并赋予角色。
