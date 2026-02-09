# 角色与项目成员 — 测试流程与用例

以下流程与用例基于《角色管理与项目成员实现分析》，覆盖：用户创建、系统角色、项目创建人/项目经理/成员、权限分配与校验。

---

## 一、测试前准备

- **环境**：后端已启动，Flyway 已执行（含 V10/V11/V12）；前端可选用或仅用接口。
- **初始数据建议**：
  - 至少 1 个 **SYSTEM_ADMIN**（如 admin），用于创建用户与访问管理后台。
  - 至少 1 个 **PMO**、1 个 **USER**（普通用户），用于后续项目与成员操作。
- **工具**：浏览器 + 前端页面，或 Postman/curl 调用接口（需先登录拿到 Session/Cookie）。

---

## 二、测试流程（推荐顺序）

### 流程 1：仅管理员可创建用户并分配系统角色

1. 使用 **admin（SYSTEM_ADMIN）** 登录。
2. 进入「用户管理」，点击「新增用户」。
3. 填写：登录账号、姓名、**角色**选择（系统管理员 / PMO / 审计 / 普通用户），保存。
4. 预期：用户创建成功，列表中可见，角色展示正确。
5. 使用 **PMO 或普通用户** 登录，尝试访问「用户管理」或 `GET /api/admin/users`。
6. 预期：无入口或 403（仅 SYSTEM_ADMIN 可访问）。

**覆盖点**：用户创建入口、系统角色赋值（role_code）、AdminInterceptor 仅放行 SYSTEM_ADMIN。

---

### 流程 2：创建项目时创建人自动成为项目经理（owner）

1. 使用 **普通用户 A（USER）** 登录。
2. 创建项目：填写项目令号、项目名称，提交。
3. 预期：项目创建成功。
4. 进入该项目「成员管理」。
5. 预期：成员列表中有 **用户 A**，角色为 **负责人（owner）**；且项目详情/列表中“当前项目经理”为该用户。
6. 查询 DB：`project.created_by_user_id = A的id`；`auth_project_acl` 中有一条 (project_id, A的id, role=owner)。

**覆盖点**：createProject 写入 created_by_user_id 并插入 ACL owner；前端展示当前项目经理。

---

### 流程 3：项目责任人添加成员并指定项目经理/编辑/查看

1. 使用 **项目创建人（或该项目 ACL owner）** 登录。
2. 进入项目详情 → 成员管理 → 添加成员。
3. 从下拉选择 **用户 B**，角色选 **负责人（owner）**，保存。
4. 预期：成员列表更新，用户 B 为负责人；若之前已有 owner，原 owner 应被替换（每项目一个 owner）。
5. 再添加 **用户 C** 为 **编辑**，**用户 D** 为 **查看**，保存。
6. 预期：列表中有 A/B/C/D，角色分别为（若 A 仍是创建人则可能仍有一条 owner 或仅 B 为 owner，以实现为准）、owner、editor、viewer。
7. 使用 **用户 C** 登录：应能上传/提交证据，不能归档/作废、不能管理成员。
8. 使用 **用户 D** 登录：不能上传/提交/归档/作废/管理成员，只能看项目与证据列表/详情。

**覆盖点**：addOrUpdateMember、ACL 唯一 owner、PermissionUtil 按 owner/editor/viewer 区分权限。

---

### 流程 4：PMO 可管理任意项目成员并批量分配

1. 使用 **PMO** 登录。
2. 进入 **非自己创建且非 ACL 成员** 的项目详情 → 成员管理。
3. 预期：仍可添加/编辑/移除成员（PMO 有 canManageMembers）。
4. 调用批量接口：`POST /api/projects/batch-members`，body：`{ "userId": 某用户id, "projectIds": [id1, id2, id3], "role": "editor" }`。
5. 预期：该用户被加入三个项目，角色均为 editor；返回 successCount/failCount/errors。
6. 使用 **普通用户** 调用同一批量接口。
7. 预期：403（仅 PMO 或系统管理员可批量分配）。

**覆盖点**：checkCanManageMembers 对 PMO 放行；batch-members 仅 PMO/SYSTEM_ADMIN；批量写入 auth_project_acl。

---

### 流程 5：一个项目批量添加多人（含项目经理）

1. 使用 **项目责任人或 PMO** 登录。
2. 调用 `POST /api/projects/{projectId}/members/batch`，body：`{ "members": [ {"userId": u1, "role": "owner"}, {"userId": u2, "role": "editor"} ] }`。
3. 预期：u1 成为该项目 owner（原 owner 被替换），u2 为 editor；返回 successCount 等。
4. 成员列表中可见 u1、u2 及对应角色。

**覆盖点**：batch AddProjectMembers、每项目唯一 owner 在批量中的表现。

---

### 流程 6：非项目成员无权限、无可见性

1. 使用 **普通用户 E**（未加入任何项目且非 PMO/ADMIN）登录。
2. 项目列表：应仅包含 E 创建的项目（若有）；不应看到其他项目。
3. 直接请求 `GET /api/projects/{其他项目id}`，预期 403。
4. 直接请求 `POST /api/projects/{其他项目id}/members`，预期 403。

**覆盖点**：getVisibleProjectIds 仅创建人+ACL；接口校验可见性与管理成员权限。

---

### 流程 7：不能修改自己在项目中的角色、管理员不能改自己

1. 使用 **项目责任人** 登录，在成员管理中尝试**把自己**从 owner 改为 editor 或尝试“添加自己”。
2. 预期：接口返回 403 或 400（不能修改自己）。
3. 使用 **admin** 登录，在用户管理中尝试禁用/改角色/删除 **admin** 自己。
4. 预期：403，提示不允许对自己的账号执行该操作。

**覆盖点**：addOrUpdateMember 禁止 operator 为自己；AdminUserService 禁止自我操作与 admin 改 admin。

---

### 流程 8：审计角色仅看作废证据入口、无项目内操作权

1. 使用 **AUDITOR** 登录。
2. 预期：有「作废证据」入口，可进入只读列表；无项目内上传/提交/归档/作废/管理成员权限。
3. 若 AUDITOR 被加入某项目 ACL 为 editor（通过 PMO 或接口直接写 DB 模拟）：按当前实现，项目内权限按 ACL 算，可能仍有上传等权限；若产品要求 AUDITOR 一律只读，需单独在 PermissionUtil 中对 AUDITOR 做限制（当前为“AUDITOR 全 false”在 computeProjectPermissionBits 中已实现，接口校验应一致）。

**覆盖点**：role_code=AUDITOR 时 can* 全 false；作废证据入口可见。

---

## 三、用例清单（可勾选执行）

### 用户创建与系统角色

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| U1 | 仅 SYSTEM_ADMIN 可访问用户管理 | admin/PMO/USER 已存在 | PMO 或 USER 访问用户管理/GET /api/admin/users | 无入口或 403 |
| U2 | 管理员创建用户并指定系统角色 | admin 已登录 | 新增用户，角色选 PMO/审计/普通用户 | 创建成功，role_code 正确 |
| U3 | 创建用户时角色必填且合法 | admin 已登录 | 提交 roleCode 为空或非法值 | 400，提示角色不合法 |
| U4 | 管理员不能禁用/改角色/删自己 | admin 已登录 | 对 admin 执行禁用/改角色/删除 | 403，提示不允许对自己操作 |
| U5 | 任意登录用户不能改自己 | 用户 A 已登录 | 对用户 A 执行禁用/改角色/删除 | 403 |

### 项目创建与自动项目经理

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| P1 | 创建项目后创建人为项目经理 | 用户 A 已登录 | 创建项目 | 项目存在，created_by_user_id=A；ACL 中 A 为 owner |
| P2 | 项目详情展示当前项目经理 | 项目已存在且有 owner | 打开项目详情/成员列表 | 当前项目经理展示正确（创建人或 ACL owner） |

### 项目成员添加与角色

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| M1 | 项目责任人可添加成员 | 用户 A 为项目创建人或 owner | A 登录，成员管理添加 B 为 editor | 成功，B 在 ACL 中为 editor |
| M2 | 指定新 owner 时原 owner 被替换 | 项目已有 owner 为 A | 添加 B 为 owner | 仅 B 为 owner，A 的 owner 被删或改为非 owner |
| M3 | PMO 可为任意项目添加成员 | PMO 已登录，项目非其创建 | 成员管理添加用户 C | 成功 |
| M4 | 非责任人不可添加成员 | 用户 D 仅为该项目 viewer | D 登录，调用 POST .../members | 403 |
| M5 | 成员选择器列出全部 sys_user | 有管理成员权限的用户已登录 | 打开添加成员，拉取用户列表 | GET /api/users 返回全部用户，下拉可选 |
| M6 | 不能添加自己为成员 | 用户 A 为责任人 | A 添加 A 自己为 editor | 403 或 400 |
| M7 | 每项目至少保留一名 owner | 项目仅创建人一条 owner | 尝试移除创建人（或唯一 owner） | 400，至少保留一名 owner |

### 项目内权限（上传/提交/归档/作废/管理成员）

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| R1 | owner 可上传/提交/归档/作废/管理成员 | 用户 A 为项目 owner | A 执行上传、提交、归档、作废、添加成员 | 均成功 |
| R2 | editor 可上传/提交，不可归档/作废/管理成员 | 用户 B 为项目 editor | B 执行上传、提交、归档、作废、添加成员 | 前二成功，后三 403 |
| R3 | viewer 仅可查看 | 用户 C 为项目 viewer | C 执行上传、提交、归档、作废、添加成员 | 均 403；列表/详情可看 |
| R4 | SYSTEM_ADMIN 对任意项目全权限 | admin 已登录 | 对任意项目执行上传/归档/作废/管理成员 | 均成功 |
| R5 | PMO 可管理成员，证据权限按项目角色 | PMO 已登录，PMO 非该项目成员 | 管理成员成功；上传/归档 按是否 owner 或 editor | 管理成员成功；无 ACL 时无上传/归档 |

### 可见项目

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| V1 | 普通用户仅见自己创建+ACL 项目 | 用户 A 仅加入项目 1、2 | A 请求项目列表/详情 | 仅见项目 1、2 |
| V2 | PMO 可见全部项目 | PMO 已登录 | 请求项目列表 | 全部项目 |
| V3 | 直接访问不可见项目详情返回 403 | 用户 A 未加入项目 3 | GET /api/projects/3 | 403 |

### 批量接口

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| B1 | PMO 可批量分配用户到多项目 | PMO 已登录 | POST /api/projects/batch-members，userId+projectIds+role | 成功，返回 successCount/failCount/errors |
| B2 | 普通用户调用 batch-members 返回 403 | 普通用户已登录 | 同上 | 403 |
| B3 | 批量为一个项目添加多人 | 责任人已登录 | POST .../projects/{id}/members/batch，members 含 owner+editor | 成功，owner 唯一 |

### 审计与边界

| 编号 | 用例描述 | 前置条件 | 操作步骤 | 预期结果 |
|------|----------|----------|----------|----------|
| A1 | AUDITOR 可看作废证据入口 | AUDITOR 已登录 | 访问作废证据页/列表 | 可进入，只读 |
| A2 | 用户不存在时添加成员失败 | 责任人已登录 | POST members，userId 为不存在的 id | 400 用户不存在 |
| A3 | 重复添加同一用户同一项目 | 用户 B 已在项目 1 为 editor | 再次添加 B 为 viewer | 更新为 viewer（幂等更新） |

---

## 四、接口级快速校验（可选）

若仅用接口测试，建议顺序：

1. **登录**：`POST /api/auth/login`，body `{ "username": "admin", "password": "..." }`，记录 Cookie/Session。
2. **创建用户**：`POST /api/admin/users`，body 含 roleCode=PMO 或 USER。
3. **创建项目**：换 USER 登录后 `POST /api/projects`，code/name 必填。
4. **成员列表**：`GET /api/projects/{id}/members`，应含创建人且 role=owner。
5. **添加成员**：`POST /api/projects/{id}/members`，body `{ "userId": 某id, "role": "editor" }`。
6. **批量分配**：用 PMO 登录，`POST /api/projects/batch-members`，body `{ "userId", "projectIds", "role" }`。
7. **权限校验**：用 editor 账号 `POST /api/evidence/upload` 上传应成功；`POST .../archive` 或管理成员应 403。

按上述流程与用例执行即可覆盖当前实现中「用户创建、系统角色、项目经理与项目成员分配、权限与可见性」的主要逻辑。
