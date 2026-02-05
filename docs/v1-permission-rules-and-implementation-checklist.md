# V1 用户/角色/权限模块 — 规则定稿与落地改造清单（核对与评估报告）

> 基于当前数据库与前后端现状，对照《V1 规则定稿 + 落地改造清单》逐条核对，不直接改代码，仅输出差异、已实现/未实现、迁移风险、可执行性与测试矩阵。

---

## V1 最终口径摘要（权威结论）

- **SYSTEM_ADMIN**：Root，全放行（系统级 + 项目级），不依赖 ACL。
- **PMO**：治理权限（看全部项目、管理成员、导入、分配项目经理），**不自动拥有任何证据动作位**；仅当 PMO 在某项目是 created_by 或 ACL owner/editor 时，才获得该项目对应证据动作位。
- **AUDITOR**：只读入口（作废证据/审计页可进入），所有 can\* 动作位为 false（canUpload/canSubmit/canArchive/canInvalidate/canManageMembers 均 false）。
- **PROJECT_\***：冻结废弃，不再参与任何权限判断；存量用户需迁移到 AUDITOR 或提供短期兼容入口。
- **项目经理 = ACL 唯一 owner**：V1 **必须**保证每项目最多一个 owner；分配 PM 通过「删旧 owner + 增新 owner」事务实现。
- **前端按钮只读后端 permissions**；**后端动作接口必须按 permissions 同源校验**（尤其 **archiveEvidence 必修复越权**）。

---

## 一、与 V1 规则的差异清单（高/中/低风险）

### 1.1 高风险

| # | 差异项 | V1 规则 | 当前实现 | 风险说明 |
|---|--------|---------|----------|----------|
| H1 | **sys_user 与 auth_user 强关联** | sys_user.auth_user_id(UUID) 唯一关联 auth_user.id；登录态解析只读该字段。 | 无 auth_user_id 字段；全链路靠 username → authUserMapper.selectByUsername 解析 UUID。 | username 不一致或 auth_user 缺失即权限解析失败；改名/数据不同步即断链。 |
| H2 | **归档权限与作废一致** | archiveEvidence 必须按 canArchive 校验（与 canInvalidate 同源：仅责任人）。 | EvidenceService.archiveEvidence 仅调用 checkProjectAccess(projectId, userId)，任意项目成员可调接口归档。 | editor/viewer 可直调接口归档，存在越权；与前端“仅责任人可归档”不一致。 |
| H3 | **列表接口返回权限位** | 证据列表 page/list 每条 VO 带 permissions（至少列表涉及按钮的位）。 | listEvidences、pageEvidence 返回的 EvidenceListItemVO **未设置** canInvalidate/canArchive/canSubmit/canUpload。 | 列表页若有操作按钮无法按权限显隐，只能猜或全显，易漏控。 |
| H4 | **PMO 不自动拥有证据动作位** | PMO 仅 canManageMembers=true；canUpload/canSubmit/canArchive/canInvalidate 默认 false，除非该项目内是 created_by 或 ACL owner/editor。 | ProjectService.getProjectDetail 中 isOwnerOrAdmin = SYSTEM_ADMIN \|\| **PMO** \|\| created_by \|\| acl owner，导致 canInvalidate、canManageMembers 对 PMO 全为 true。 | PMO 被当成“项目责任人”，可作废/归档，与 V1“治理角色、不自动拥有证据动作”冲突。 |

### 1.2 中风险

| # | 差异项 | V1 规则 | 当前实现 | 风险说明 |
|---|--------|---------|----------|----------|
| M1 | **权限位统一结构与命名** | 项目详情返回 projectPermissions（canUpload/canSubmit/canArchive/canInvalidate/canManageMembers）；证据详情返回 evidencePermissions（同结构或子集）。 | ProjectVO 仅有 canInvalidate、canManageMembers，**无 canUpload/canSubmit/canArchive**；EvidenceListItemVO 仅有 canInvalidate。 | 前端无法统一用 permissions 控制上传/提交/归档；结构零散易漏。 |
| M2 | **上传按钮仅 canUpload 显隐** | 前端上传按钮必须用 projectPermissions.canUpload 控制。 | ProjectDetail.vue 中「上传证据」按钮**无 v-if**，所有能进项目详情的人都能看到；viewer 点击后后端 403。 | 体验差、易误解；与“按钮只吃后端权限位”不符。 |
| M3 | **作废证据页入口** | 入口：SYSTEM_ADMIN + AUDITOR（只读）；页面提示“可查看不等于可作废/可操作”；按钮仍按 canInvalidate 等权限位。 | 路由/菜单用 VOIDED_EVIDENCE_ROLES = [SYSTEM_ADMIN, PROJECT_OWNER, PROJECT_AUDITOR]；无 AUDITOR；无“可查看不等于可操作”文案。 | 与“冻结 PROJECT_*、AUDITOR 只读入口”不一致；缺 PMO/普通用户策略。 |
| M4 | **登录态解析唯一方式** | 从 sys_user.auth_user_id 取 authUserId(UUID)；过渡期 fallback username 须打日志并告警。 | 无 auth_user_id；所有需 UUID 处均 resolveCreatedByUuid(username)；无统一“从请求取 authUserId”入口。 | 多处传 username，易漏；无法落实“唯一方式”。 |
| M5 | **AUDITOR 全部动作位 false** | AUDITOR 仅可进审计/作废证据只读页；canXXX 全为 false。 | 后端 PermissionUtil 未单独处理 AUDITOR；若某用户 roleCode=AUDITOR 且无 ACL，getProjectDetail/getEvidenceById 会按“非 owner”算，canInvalidate 等已为 false，但入口尚未改为 AUDITOR。 | 需明确 AUDITOR 不参与任何 canXXX 计算，并统一入口。 |

### 1.3 低风险

| # | 差异项 | V1 规则 | 当前实现 | 风险说明 |
|---|--------|---------|----------|----------|
| L1 | **冻结 PROJECT_* 不参与权限计算** | PROJECT_* 冻结废弃，不再参与任何权限判断；存量用户需迁移到 AUDITOR 或提供短期兼容入口。 | 后端 PermissionUtil 已未用 PROJECT_* ✓；前端路由/菜单/ROLE_OPTIONS/Me.vue 仍用 PROJECT_*。 | 需前端入口与角色展示收敛为 SYSTEM_ADMIN/PMO/AUDITOR；存量 PROJECT_* 用户迁移或兼容入口。 |
| L2 | **ACL editor 权限位** | editor：canUpload/canSubmit=true；canArchive/canInvalidate/canManageMembers=false。 | checkCanUpload 通过 checkProjectPermission(requireUpload=true)，即 editor 可上传/提交；未显式输出 canArchive/canInvalidate/canManageMembers=false。 | 需在统一 permissionBits 输出时按角色明确各位。 |
| L3 | **分配项目经理（唯一 owner）** | V1 必须：每项目最多 1 个 owner；assign PM = 删旧 owner + 增新 owner 事务；SYSTEM_ADMIN/PMO 允许。 | addOrUpdateMember/removeMember 已存在；checkCanManageMembers 允许 PMO ✓；**未**强制每项目最多 1 个 owner。 | V1 必做：服务层保证每项目最多 1 个 owner；分配 PM 用事务「删旧 owner + 增新 owner」。 |
| L4 | **ROLE_OPTIONS 与 roleLabel** | 用户分配角色：SYSTEM_ADMIN/PMO/AUDITOR（及普通用户）；PROJECT_* 不再出现或标“已废弃”。 | adminUsers.ts ROLE_OPTIONS 仅有 SYSTEM_ADMIN + 4 个 PROJECT_*，**缺 PMO、AUDITOR**；Me.vue roleLabels 同样缺 PMO、AUDITOR。 | 需补齐 PMO、AUDITOR 展示与选项；PROJECT_* 标记废弃或移除。 |

---

## 二、已满足 vs 未实现（逐项列出文件/表/接口位置）

### 2.1 已满足

| 规则条 | 位置 | 说明 |
|--------|------|------|
| 项目内权限只看 created_by + ACL | PermissionUtil（checkCanInvalidate/checkCanManageMembers/checkCanUpload）、EvidenceService.getVisibleProjectIds | 未用 PROJECT_* 参与项目内权限计算。 |
| 业务表继续使用 UUID | project.created_by, evidence_item.created_by, auth_project_acl.user_id, V1__init / V2 | 均为 UUID 引用 auth_user.id。 |
| 不做 sys_role 表化 | — | V1 不引入，符合。 |
| 上传/提交/作废/管理成员接口校验 | EvidenceService.uploadEvidence(checkCanUpload)、submitEvidence(checkCanSubmit)、invalidateEvidence(checkCanInvalidate)；ProjectService.addOrUpdateMember/removeMember(checkCanManageMembers) | 动作接口已用 PermissionUtil 校验（除归档）。 |
| 项目详情返回 canInvalidate、canManageMembers | ProjectService.getProjectDetail → ProjectVO.canInvalidate, canManageMembers | 有返回，但 PMO 被误赋 true，且缺 canUpload/canSubmit/canArchive。 |
| 证据详情返回 canInvalidate | EvidenceService.getEvidenceById → EvidenceListItemVO.canInvalidate | 有返回；缺 canSubmit/canArchive 及统一 evidencePermissions 结构。 |
| 成员管理入口用后端权限位 | ProjectDetail.vue 成员管理按钮 v-if="project?.canManageMembers"；ProjectMembers.vue canManageMembers 来自接口 | 已用 canManageMembers 控制。 |
| 管理后台仅 SYSTEM_ADMIN | AdminInterceptor 对 /api/admin/** 校验；前端路由 meta.requiresAuth | 后端 403、前端需登录。 |
| PMO 可见全部项目 | EvidenceService.getVisibleProjectIds：SYSTEM_ADMIN \|\| PMO → 全部项目 | 已实现。 |
| PMO 可管理成员 | PermissionUtil.checkCanManageMembers：SYSTEM_ADMIN \|\| PMO 放行 | 已实现。 |

### 2.2 未实现（需改造）

| 规则条 | 类型 | 位置 | 改造要点 |
|--------|------|------|----------|
| sys_user.auth_user_id | 数据库 | sys_user 表、V4__init_user_and_audit.sql | 新增列 auth_user_id UUID NULL；Flyway 迁移 + 回填脚本。 |
| 登录态解析从 auth_user_id 取 UUID | 后端 | AuthUserVO、AuthService.getCurrentUser、各 Controller/Service 入参 | AuthUserVO 增加 authUserId；getCurrentUser 从 SysUser 取 auth_user_id；需 UUID 的接口改为取 user.getAuthUserId()，fallback username 时打日志。 |
| 统一 projectPermissions/evidencePermissions 结构 | 后端 | ProjectVO、EvidenceListItemVO；ProjectService.getProjectDetail；EvidenceService.getEvidenceById、listEvidences、pageEvidence | 增加 projectPermissions/evidencePermissions（或扁平 5 个 can*）；getProjectDetail 按 V1 规则计算 PMO/owner/editor/viewer 各位；getEvidenceById 同上；listEvidences/pageEvidence 每条设置 permissions。 |
| PMO 不自动给证据动作位 | 后端 | ProjectService.getProjectDetail；EvidenceService.getEvidenceById；PermissionUtil 或统一 PermissionService | canInvalidate/canUpload/canSubmit/canArchive 对 PMO 仅当其在该项目为 created_by 或 ACL owner/editor 时为 true；canManageMembers 对 PMO 保持 true。 |
| archiveEvidence 按 canArchive 校验 | 后端 | EvidenceService.archiveEvidence；EvidenceVersionController.archiveEvidence | 将 checkProjectAccess 改为 checkCanArchive（与 checkCanInvalidate 同源：责任人）；或新增 checkCanArchive 复用责任人逻辑。 |
| 证据列表每条带 permissions | 后端 | EvidenceService.listEvidences、pageEvidence | 对每条 EvidenceListItemVO 设置 canInvalidate/canArchive/canSubmit/canUpload（或 permissions 对象）。 |
| 上传按钮用 canUpload | 前端 | ProjectDetail.vue（上传证据按钮） | 增加 v-if="project?.canUpload" 或 project?.permissions?.canUpload；需后端先返回 canUpload。 |
| 提交/归档/作废按钮仅用权限位 | 前端 | EvidenceDetail.vue | canSubmit 除状态 DRAFT 外应结合 evidence.permissions.canSubmit；canArchive/canVoid 已用 canInvalidate，可改为 evidence.permissions 统一字段。 |
| 作废证据页入口 SYSTEM_ADMIN + AUDITOR | 前端 | router/index.ts VOIDED_EVIDENCE_ROLES；stores/auth.ts canAccessVoidedEvidence | 改为 ['SYSTEM_ADMIN', 'AUDITOR']；移除 PROJECT_OWNER、PROJECT_AUDITOR。 |
| 作废证据页文案“可查看不等于可作废/可操作” | 前端 | VoidedEvidenceList.vue（或作废证据页） | 增加固定提示文案。 |
| 路由/菜单不再用 PROJECT_* | 前端 | router/index.ts、MainLayout/EvidenceHome 等菜单 | 治理入口：SYSTEM_ADMIN + PMO；审计入口：SYSTEM_ADMIN + AUDITOR；用户管理：SYSTEM_ADMIN。 |
| ROLE_OPTIONS 与 roleLabel 含 PMO、AUDITOR | 前端 | frontend/src/api/adminUsers.ts ROLE_OPTIONS；Me.vue roleLabels | 增加 PMO、AUDITOR；PROJECT_* 标废弃或从分配选项移除。 |
| 每项目最多 1 个 owner（V1 必做） | 后端 | ProjectService.addOrUpdateMember / 分配 PM 逻辑 | V1 必须：分配 PM = 事务内删旧 owner + 增新 owner；新增/修改成员时校验每项目最多 1 个 owner。 |

---

## 三、数据迁移风险：auth_user_id 回填

### 3.1 回填逻辑

- **关联条件**：`sys_user.username = auth_user.username`（两表均有 username，auth_user.username UNIQUE）。
- **脚本**：`UPDATE sys_user su SET auth_user_id = au.id FROM auth_user au WHERE su.username = au.username;`

### 3.2 风险报表（需在回填前后执行）

| 检查项 | SQL（示例） | 处理建议 |
|--------|-------------|----------|
| **回填前：sys_user 无对应 auth_user** | `SELECT su.id, su.username FROM sys_user su LEFT JOIN auth_user au ON su.username = au.username WHERE au.id IS NULL AND su.is_deleted = false;` | 为这些 sys_user 在 auth_user 中补齐记录（或禁用 sys_user），否则回填后 auth_user_id 为 NULL。 |
| **回填后：auth_user_id 为 NULL** | `SELECT id, username, auth_user_id FROM sys_user WHERE auth_user_id IS NULL AND is_deleted = false;` | 必须报表并处理（创建 auth_user 或禁用账号）；否则登录后无法解析 UUID。 |
| **回填后：auth_user_id 重复** | `SELECT auth_user_id, COUNT(*) FROM sys_user WHERE auth_user_id IS NOT NULL GROUP BY auth_user_id HAVING COUNT(*) > 1;` | 若业务允许多 sys_user 对应同一 auth_user（如多入口登录），则不加 UNIQUE；否则人工处理重复后再加 UNIQUE。 |

### 3.3 当前数据假设（基于 init-evidence-test-data.sql）

- auth_user：admin, owner1, editor1, viewer1, auditor1（ON CONFLICT DO NOTHING）。
- sys_user：V4 插入 admin；init-evidence-test-data 插入 owner1, editor1, viewer1, auditor1。
- 若两套脚本均执行，username 一一对应，回填后理论上无 NULL；但生产环境可能存在仅 sys_user 存在、auth_user 未同步的情况，**必须先跑“回填前”检查**。

### 3.4 约束策略

- 先可空、回填、校验；稳定后再考虑 `UNIQUE(auth_user_id)`、`NOT NULL`（在无 NULL 且无重复前提下）。

---

## 四、可执行性结论与分阶段实施步骤

### 4.1 结论

- **可执行**，但需满足：
  1. **先完成 auth_user_id 回填与校验**（处理 NULL 与重复），再全面切换为 auth_user_id 解析；此前可保留 username fallback 并打日志。
  2. **产品确认**：PMO 在“非该项目 ACL/created_by”时是否仅 canManageMembers、无证据动作位（当前已按此规则定稿）；作废证据入口仅 SYSTEM_ADMIN + AUDITOR；PROJECT_* 仅展示或废弃。

### 4.2 分阶段步骤（最小破坏）

**Phase 1 — 修桥（DB + 登录态解析）**

1. **DB**  
   - Flyway：`ALTER TABLE sys_user ADD COLUMN auth_user_id UUID NULL;`  
   - 回填：按 username 关联 auth_user 更新 auth_user_id。  
   - 校验：执行 3.2 中 NULL/重复 SQL，处理异常后再考虑 UNIQUE/NOT NULL。

2. **后端**  
   - SysUser 实体、SysUserMapper（select/insert/update）支持 auth_user_id。  
   - AuthUserVO 增加 authUserId(UUID)；AuthService.getCurrentUser 从 SysUser 取 auth_user_id 写入 VO。  
   - 所有需要“当前用户 UUID”的 Controller/Service：优先 user.getAuthUserId()，为 null 时 fallback resolveCreatedByUuid(username) 并打日志。  
   - 不改动权限位计算与接口语义，仅切换“用户 UUID 来源”。

**Phase 2 — 权限位统一输出与前端按钮**

1. **后端**  
   - 统一权限计算（PermissionUtil 或 PermissionService）：按 V1 规则计算 canUpload/canSubmit/canArchive/canInvalidate/canManageMembers（PMO 仅 canManageMembers 默认 true，证据位仅在该项目 created_by/ACL 时 true；AUDITOR 全 false）。  
   - GET /api/projects/{id}：返回 projectPermissions（5 个位）或扁平到 ProjectVO。  
   - GET /api/evidence/{id}：返回 evidencePermissions（或 EvidenceListItemVO 内 5 个位）。  
   - listEvidences、pageEvidence：每条设置 permissions（至少列表用到的位）。

2. **前端**  
   - 上传按钮：v-if 使用 project.canUpload 或 project.permissions?.canUpload。  
   - 提交/归档/作废：使用 evidence.permissions 对应位（可兼容现有 canInvalidate）。  
   - 成员管理：继续使用 canManageMembers（或 permissions.canManageMembers）。

**Phase 3 — 修不一致与入口统一**

1. **后端**  
   - archiveEvidence：改为 checkCanArchive（与 checkCanInvalidate 同源：仅责任人），与前端 canArchive 一致（**必修复越权**）。  
   - **每项目最多 1 个 owner**：分配 PM 通过事务「删旧 owner + 增新 owner」实现；新增/调整成员时校验 owner 数量。

2. **前端**  
   - 作废证据页入口：VOIDED_EVIDENCE_ROLES = ['SYSTEM_ADMIN', 'AUDITOR']；canAccessVoidedEvidence 同步。  
   - 作废证据页增加文案：“可查看不等于可作废/可操作”。  
   - 路由/菜单：治理入口 SYSTEM_ADMIN + PMO；审计入口 SYSTEM_ADMIN + AUDITOR；用户管理 SYSTEM_ADMIN。  
   - ROLE_OPTIONS 与 Me.vue roleLabels：增加 PMO、AUDITOR；PROJECT_* 标废弃或移除。

---

## 五、最小回归测试矩阵

| 身份 | 可见项目 | canUpload | canSubmit | canArchive | canInvalidate | canManageMembers | 作废证据页入口 | 用户管理页 |
|------|----------|-----------|-----------|------------|---------------|------------------|----------------|------------|
| SYSTEM_ADMIN | 全部 | ✓（任意可见） | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| PMO（非项目成员） | 全部 | ✗ | ✗ | ✗ | ✗ | ✓（任意可见项目） | ✗（按 V1 入口为 AUDITOR） | ✗ |
| PMO（某项目 ACL owner） | 全部 | ✓（该项目） | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ |
| AUDITOR | 按 ACL/created_by | ✗ | ✗ | ✗ | ✗ | ✗ | ✓（只读） | ✗ |
| 项目 created_by | 自己的项目 | ✓ | ✓ | ✓ | ✓ | ✓ | 按方案 | ✗ |
| ACL owner | 有 ACL 的项目 | ✓ | ✓ | ✓ | ✓ | ✓ | 按方案 | ✗ |
| ACL editor | 有 ACL 的项目 | ✓ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| ACL viewer | 有 ACL 的项目 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |

**测试要点**：  
- 每个身份在“有权限/无权限”项目各测一次。  
- 归档：Phase 3 后仅责任人可成功；editor/viewer 调归档接口应 403。  
- 上传按钮：仅 canUpload 时显示；viewer 不应看到上传按钮。  
- 作废证据页：AUDITOR 可进、无操作按钮；SYSTEM_ADMIN 可进可操作（在项目有权限时）。  
- 列表页：若有操作按钮，必须用 permissions 控制，且后端列表接口已带 permissions。

---

## 六、必做改动清单汇总（便于排期）

### 后端（Java / SQL）

| 阶段 | 文件/模块 | 改动 |
|------|-----------|------|
| Phase 1 | Flyway 迁移 | ADD COLUMN sys_user.auth_user_id UUID NULL；回填脚本；校验查询。 |
| Phase 1 | SysUser.java | 增加 authUserId (UUID)。 |
| Phase 1 | SysUserMapper.xml / 接口 | select/insert/update 含 auth_user_id。 |
| Phase 1 | AuthUserVO.java | 增加 authUserId。 |
| Phase 1 | AuthService.getCurrentUser | 从 SysUser 取 auth_user_id 写入 VO。 |
| Phase 1 | ProjectController / EvidenceController / EvidenceVersionController | 需 UUID 时改为 user.getAuthUserId()，null 时 fallback + 日志。 |
| Phase 1 | EvidenceService / ProjectService | 入参或从 request 取 authUserId，替代 resolveCreatedByUuid(username) 为主路径。 |
| Phase 2 | PermissionUtil 或 PermissionService | 按 V1 规则计算 5 个权限位（PMO/AUDITOR/owner/editor/viewer）；可选 getProjectRole(projectId, authUserId)。 |
| Phase 2 | ProjectService.getProjectDetail | 返回 projectPermissions（5 位），PMO 仅 canManageMembers 默认 true。 |
| Phase 2 | EvidenceService.getEvidenceById | 返回 evidencePermissions（5 位）。 |
| Phase 2 | EvidenceService.listEvidences、pageEvidence | 每条设置 permissions。 |
| Phase 2 | ProjectVO / EvidenceListItemVO | 增加 projectPermissions/evidencePermissions 或扁平 canUpload 等。 |
| Phase 3 | EvidenceService.archiveEvidence | 校验改为 checkCanArchive（与 checkCanInvalidate 同源），**必修复越权**。 |
| Phase 3 | EvidenceVersionController.archiveEvidence | 传入 roleCode，调用带 roleCode 的 archiveEvidence 或内部解析当前用户。 |
| Phase 3 | ProjectService 分配 PM / addOrUpdateMember | V1 必做：每项目最多 1 个 owner；分配 PM = 事务内删旧 owner + 增新 owner；新增/调整成员时校验 owner 数。 |

### 前端（Vue/TS）

| 阶段 | 文件/模块 | 改动 |
|------|-----------|------|
| Phase 2 | ProjectDetail.vue | 上传按钮 v-if 使用 project.canUpload 或 project.permissions?.canUpload。 |
| Phase 2 | EvidenceDetail.vue | 提交/归档/作废使用 evidence.permissions 对应位（兼容现有 canInvalidate）。 |
| Phase 2 | api/projects.ts、evidence 类型 | ProjectVO/EvidenceListItem 类型增加 canUpload/canSubmit/canArchive 或 permissions。 |
| Phase 3 | router/index.ts | VOIDED_EVIDENCE_ROLES = ['SYSTEM_ADMIN', 'AUDITOR']；作废证据页守卫。 |
| Phase 3 | stores/auth.ts | canAccessVoidedEvidence 改为 SYSTEM_ADMIN 或 AUDITOR。 |
| Phase 3 | VoidedEvidenceList.vue | 增加“可查看不等于可作废/可操作”提示。 |
| Phase 3 | api/adminUsers.ts、Me.vue | ROLE_OPTIONS 与 roleLabels 增加 PMO、AUDITOR；PROJECT_* 标废弃或移除。 |
| Phase 3 | 菜单/入口 | 治理入口 SYSTEM_ADMIN + PMO；审计入口 SYSTEM_ADMIN + AUDITOR。 |

### 数据 / SQL

| 阶段 | 内容 |
|------|------|
| Phase 1 | 回填脚本；回填前“无匹配 sys_user”查询；回填后 NULL/重复校验；可选 UNIQUE/NOT NULL（在无问题后）。 |

---

以上为基于当前代码与库表的 V1 规则定稿核对与落地改造清单，可直接用于排期与实施决策；实施时按 Phase 1 → 2 → 3 顺序执行，并完成 3.2 中数据迁移校验与五中回归测试矩阵。
