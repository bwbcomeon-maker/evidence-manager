# 角色、权限、用户与项目分配 — 现状与未实现项梳理

## 一、当前两套“角色”概念（易混淆）

| 维度 | 表/来源 | 取值 | 用途 |
|------|--------|------|------|
| **全局角色** | `sys_user.role_code` | SYSTEM_ADMIN, PMO, PROJECT_OWNER, PROJECT_EDITOR, PROJECT_VIEWER, PROJECT_AUDITOR | 登录身份、路由/菜单可见性、管理员后台、可见项目范围（如 PMO/SYSTEM_ADMIN 看全部项目） |
| **项目内角色** | `auth_project_acl.role` | owner, editor, viewer | 项目内权限：谁可上传/提交、谁可作废、谁可管理成员 |

二者**无绑定关系**：例如 `sys_user.role_code = PROJECT_OWNER` 并不等于“在某个项目里是 ACL owner”。项目内权限只看「是否项目创建人」或「ACL 中该用户的 role」。

---

## 二、已实现的功能（简要）

- **可见项目范围**：SYSTEM_ADMIN / PMO 看全部；其他用户 = 自己创建的项目 + 自己在 ACL 中的项目（按 auth_user UUID）。
- **作废证据**：仅 SYSTEM_ADMIN 或 项目 created_by 或 ACL owner；必填原因；审计有 before/after。
- **管理成员**：仅 SYSTEM_ADMIN、PMO、项目 created_by、ACL owner；不能改自己；列表返回 isCurrentUser。
- **上传/提交证据**：SYSTEM_ADMIN 或 项目内 owner/editor（viewer 不可）；后端 checkCanUpload/checkCanSubmit。
- **项目详情**：返回 canInvalidate、canManageMembers（与上面逻辑一致）。
- **证据详情**：返回 canInvalidate，前端用其控制「提交/归档/作废」按钮。
- **路由级**：「作废证据」页仅 SYSTEM_ADMIN / PROJECT_OWNER / PROJECT_AUDITOR 可访问；管理员后台仅 SYSTEM_ADMIN。

---

## 三、未实现或不一致项

### 1. PMO 在前端未完整暴露

- **后端**：`AdminUserService.VALID_ROLE_CODES` 已含 PMO；`getVisibleProjectIds`、`checkCanManageMembers` 已支持 PMO。
- **前端**：
  - 用户管理（AdminUsers）的 **ROLE_OPTIONS** 中没有 PMO，无法在「用户管理」里给用户分配 PMO。
  - 「我的」页 **roleLabel** 映射里没有 PMO，PMO 用户会显示为原始 code。
- **建议**：在 `adminUsers.ts` 的 ROLE_OPTIONS 和 Me 页 roleLabels 中增加 PMO（如「PMO」或「项目群管」）。

### 2. 「作废证据」入口与真实权限不一致

- **现状**：路由/菜单用 `canAccessVoidedEvidence`（SYSTEM_ADMIN / PROJECT_OWNER / PROJECT_AUDITOR）控制「作废证据」入口。
- **实际**：能真正作废的是「项目责任人」= SYSTEM_ADMIN 或 项目 created_by 或 该项目 ACL owner；由接口返回的 `canInvalidate` 控制按钮。
- **结果**：PROJECT_AUDITOR 能进作废证据页，但若在任意项目里都不是 owner/创建人，则没有任何「作废」按钮，易困惑。
- **建议**：要么在路由/菜单上明确「仅具备项目责任人身份者可进」（需后端或前端聚合“是否有任意项目 canInvalidate”）；要么保留现状但在产品说明/文案中区分「可进入作废证据列表」与「可对某条证据执行作废」。

### 3. 上传按钮缺少“按钮级”权限控制

- **现状**：项目详情页「上传证据」对**所有能进项目详情的人**展示（仅依赖“能看项目”）。
- **后端**：上传接口已按 checkCanUpload 校验（viewer 会 403）。
- **结果**：viewer 仍能看到上传按钮，点击后报错，体验差。
- **建议**：项目详情接口增加 **canUpload**（与 checkCanUpload 逻辑一致：SYSTEM_ADMIN 或 项目内 owner/editor），前端仅当 `project.canUpload === true` 时显示「上传证据」按钮。

### 4. 归档权限前后端不一致

- **后端**：`archiveEvidence` 仅做 `checkProjectAccess`（任意有项目访问权限的人均可调接口归档）。
- **前端**：`canArchive` 依赖 `evidence.canInvalidate`（仅项目责任人显示归档按钮）。
- **结果**：editor/viewer 通过直接调接口仍可归档；若希望「只有项目责任人可归档」，需后端改为与作废一致的权限（如 checkCanInvalidate 或同一套“项目责任人”校验）。

### 5. 无统一的“项目内权限”聚合

- **现状**：canInvalidate、canManageMembers 由项目详情/证据详情等接口分别计算；上传权限未在项目详情中返回。
- **结果**：前端需多处写 v-if，且易漏（如上传按钮）；后续加新权限（如“可管理项目设置”）会继续分散。
- **建议**：在项目详情（或单独接口）中返回当前用户在该项目的权限位：如 **canUpload、canSubmit、canArchive、canInvalidate、canManageMembers**，前端统一用这些字段做按钮显隐。

### 6. 全局角色（PROJECT_OWNER / EDITOR / VIEWER / AUDITOR）未参与权限计算

- **现状**：后端 PermissionUtil 与 EvidenceService 中，只使用 **roleCode = SYSTEM_ADMIN / PMO** 做“全局放行”；项目内权限只看 **auth_project_acl.role**（owner/editor/viewer）和 **project.created_by**，**未使用** sys_user 的 PROJECT_OWNER / PROJECT_EDITOR / PROJECT_VIEWER / PROJECT_AUDITOR。
- **结果**：全局角色目前仅用于：路由/菜单（作废证据入口）、前端展示；若产品希望“全局项目负责人在任意项目都视为有 owner 权限”等，当前未实现。
- **建议**：先明确产品设计——若“全局角色”仅作展示与入口控制，可保持现状并文档化；若要与项目内权限联动，再定义规则（例如 PROJECT_OWNER 是否在所有项目都视为 owner）并落地到 PermissionUtil/接口。

### 7. 用户管理入口仅对 SYSTEM_ADMIN

- **现状**：Me 页「用户管理」用 `auth.isAdmin`（即 roleCode === SYSTEM_ADMIN）；AdminInterceptor 仅放行 SYSTEM_ADMIN。
- **结果**：若未来希望 PMO 也能进用户管理，需扩展 AdminInterceptor 与前端 isAdmin / 入口显隐逻辑。

### 8. sys_user 与 auth_user 的关联未文档化且脆弱

- **现状**：通过 **username** 关联（EvidenceService.resolveCreatedByUuid(username) 用 auth_user 的 username）；project.created_by、evidence.created_by、ACL 均为 auth_user.id (UUID)。
- **风险**：若 sys_user 与 auth_user 不是一一对应（改名、未同步创建），会导致“当前登录用户”在项目侧解析不到，权限判断异常。
- **建议**：在开发/部署文档中明确：登录用户与项目成员必须通过同一 username 对应到同一 auth_user；或后续引入唯一关联字段（如 sys_user 存 auth_user_id）。

---

## 四、建议实现优先级（仅列出未实现项）

| 优先级 | 项 | 说明 |
|--------|----|------|
| P1 | 上传按钮按权限显隐 | 项目详情返回 canUpload；前端「上传证据」仅 canUpload 时显示。 |
| P1 | PMO 在前端可分配与展示 | ROLE_OPTIONS、Me 页 roleLabel 增加 PMO。 |
| P2 | 归档权限与作废统一（可选） | 若要求仅项目责任人可归档：后端 archiveEvidence 改为 checkCanInvalidate 或同一套项目责任人校验。 |
| P2 | 项目详情返回权限聚合 | 一次返回 canUpload、canSubmit、canArchive、canInvalidate、canManageMembers，前端统一用。 |
| P3 | 作废证据入口与真实权限对齐 | 路由/菜单考虑“是否有任意项目 canInvalidate”，或保留现状并补充说明。 |
| P3 | 全局角色与项目权限是否联动 | 产品定稿后，若有联动再在 PermissionUtil/接口中实现。 |
| P3 | sys_user 与 auth_user 关联说明 | 文档化或增加关联字段，避免不同步。 |

---

## 五、快速对照：谁能在哪里做什么（当前逻辑）

| 能力 | SYSTEM_ADMIN | PMO | 项目 created_by / ACL owner | ACL editor | ACL viewer |
|------|---------------------|-----|-----------------------------|------------|------------|
| 可见项目 | 全部 | 全部 | 自己相关项目 | 自己相关项目 | 自己相关项目 |
| 上传/提交证据 | 任意可见项目 | 需在 ACL 且非 viewer | 是 | 是 | 否（后端 403） |
| 作废证据 | 任意可见项目 | 否（非项目责任人） | 是 | 否 | 否 |
| 归档证据 | 后端任意可见项目可调接口；前端仅项目责任人显示按钮 | 同上 | 是 | 后端可，前端不显示 | 后端可，前端不显示 |
| 管理成员 | 任意可见项目 | 任意可见项目 | 是 | 否 | 否 |
| 用户管理页（/admin/users） | 可访问 | 不可访问 | 不可访问 | 不可访问 | 不可访问 |
| 作废证据页（菜单入口） | 可进 | 不可进（未在 VOIDED_EVIDENCE_ROLES） | 可进（PROJECT_OWNER） | 不可进 | 不可进（PROJECT_VIEWER）；PROJECT_AUDITOR 可进 |

说明：作废证据**页**的入口由 sys_user.role_code（VOIDED_EVIDENCE_ROLES）控制；实际**作废按钮**与能否调通接口由项目责任人（created_by / ACL owner）或 SYSTEM_ADMIN 决定。
