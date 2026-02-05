# 用户/角色/权限模块 V1 重建 — 现状核对与可执行性评估

> 本文档仅做评估与核对，不直接修改代码。限制：不做 UUID→bigint 主键迁移；V1 不引入 sys_role 表化；不改 auth_project_acl 主键/枚举；新字段优先可空。

---

## 一、目标设计与当前实现的差异清单（按风险分级）

### 1.1 高风险（影响正确性、数据一致性或安全）

| # | 差异项 | 目标设计 | 当前实现 | 风险说明 |
|---|--------|----------|----------|----------|
| H1 | **sys_user 与 auth_user 关联方式** | 强关联：`sys_user.auth_user_id UUID` 指向 `auth_user.id`，登录态解析直接读字段。 | 无 `auth_user_id`；全链路靠 `username` → `authUserMapper.selectByUsername(username)` 解析 UUID。 | username 不一致或 auth_user 缺失即无法解析，权限全失效；改名/数据不同步即断链。 |
| H2 | **归档权限与作废不一致** | 归档与作废应统一：仅项目责任人可归档（或明确仅责任人可归档）。 | 后端 `archiveEvidence` 仅做 `checkProjectAccess`（任意项目成员可调接口归档）；前端用 `canInvalidate` 控制归档按钮。 | editor/viewer 可直调接口归档，与前端展示和“仅责任人可归档”预期不符，存在越权。 |
| H3 | **列表接口不返回权限位** | 列表 VO（如 EvidenceListItemVO）应带 permissionBits，避免列表页按钮漏控。 | `listEvidences`、`pageEvidence` 返回的 EvidenceListItemVO **未设置** canInvalidate/canUpload/canArchive/canSubmit。仅 `getEvidenceById` 设置 canInvalidate。 | 从列表进入或列表内若有“作废/归档/提交”等入口时，前端无法按权限显隐，只能猜或全显。 |

### 1.2 中风险（可运行但混乱、难维护或体验差）

| # | 差异项 | 目标设计 | 当前实现 | 风险说明 |
|---|--------|----------|----------|----------|
| M1 | **权限位未统一结构与命名** | 项目详情/证据详情统一返回 `projectPermissions` / `evidencePermissions`（canUpload/canSubmit/canArchive/canInvalidate/canManageMembers）。 | ProjectVO 零散字段：canInvalidate、canManageMembers；**无 canUpload/canSubmit/canArchive**。EvidenceListItemVO 仅 canInvalidate。 | 前端需多处兼容；新增权限易漏；上传按钮无法按权限显隐。 |
| M2 | **上传按钮无权限控制** | 前端上传按钮仅当 `project.permissions.canUpload` 为 true 时显示。 | 项目详情未返回 canUpload；ProjectDetail 页「上传证据」**无 v-if**，所有能进项目详情的人都能看到按钮；viewer 点击后后端 403。 | 体验差、易误解；与“按钮只吃后端权限位”不符。 |
| M3 | **作废证据页入口策略** | 方案 A：仅“具有任意项目 canInvalidate 的用户”+ SYSTEM_ADMIN 可进；或方案 B：AUDITOR 可进只读，按钮仍按 canInvalidate。 | 路由/菜单用 `canAccessVoidedEvidence` = roleCode ∈ [SYSTEM_ADMIN, PROJECT_OWNER, PROJECT_AUDITOR]，与“项目内作废权限”脱节。 | PROJECT_AUDITOR 能进页但可能无任何可作废证据；PROJECT_OWNER 全局角色与项目内 owner 混用，概念混乱。 |
| M4 | **resolveAuthUserId 唯一入口** | 从登录态直接取 auth_user_id(UUID)，不再用 username 查 auth_user。 | 无 auth_user_id 字段；所有需要 UUID 的地方都走 `EvidenceService.resolveCreatedByUuid(username)`（内部 selectByUsername）。 | 多处传 username，易漏；性能与一致性依赖 auth_user 表与 username 一致。 |

### 1.3 低风险（可延后或文档化即可）

| # | 差异项 | 目标设计 | 当前实现 | 风险说明 |
|---|--------|----------|----------|----------|
| L1 | **全局角色收敛为 SYSTEM_ADMIN/PMO/AUDITOR** | 严禁用 PROJECT_OWNER/EDITOR/VIEWER 等全局角色参与项目内权限计算；仅系统级入口/可见性可用。 | 后端 PermissionUtil **未用** PROJECT_* 做项目权限，只用 SYSTEM_ADMIN/PMO；前端路由/菜单仍用 PROJECT_OWNER、PROJECT_AUDITOR 控制「作废证据」入口；AdminUserService.VALID_ROLE_CODES 仍含全部 6 个角色。 | 若冻结 PROJECT_* 仅作展示与入口，需明确入口策略并可能改为 AUDITOR 或 hasInvalidateAnyProject。 |
| L2 | **getProjectRole(projectId, authUserId)** | 统一方法返回 owner/editor/viewer/none。 | 无独立方法；逻辑散落在 checkProjectPermission、checkCanInvalidate、checkCanManageMembers 等内部（project.created_by + acl）。 | 可收拢为 getProjectRole + 各 canXXX 基于其计算，便于维护与权限位输出。 |
| L3 | **PMO 产品规则** | 明确 PMO 是否“全放行”或仅治理不参与证据操作。 | 当前 PMO 与 SYSTEM_ADMIN 同等：getVisibleProjectIds 全项目、checkCanManageMembers 可管理成员；**未**参与 upload/submit/invalidate（仅 SYSTEM_ADMIN 放行）。 | 需产品确认；若 PMO 仅治理，当前已基本符合。 |
| L4 | **角色表化 sys_role / sys_user_role** | V1 可延后。 | 未实现；单字段 role_code。 | 按设计 V2 再做。 |

---

## 二、可执行项 vs 必须延后项

### 2.1 可执行（最小破坏路线）

- **Phase 0 冻结**：明确不再扩展 PROJECT_* 在权限计算中的含义；后端已基本满足（未用 PROJECT_* 算权限），仅需文档化并约束前端/新代码。
- **Phase 1 修桥**：  
  - **A) sys_user 增加 auth_user_id(UUID) 并回填**：新增可空列 → 回填脚本（按 username 关联 auth_user）→ 校验无重复/无遗漏 → 再考虑 UNIQUE/NOT NULL。  
  - **B) 登录态解析改为读 auth_user_id**：AuthUserVO 增加 authUserId（UUID）；getCurrentUser 从 SysUser 取 auth_user_id 写入 VO；所有需要“当前用户 UUID”的接口改为取 request 中的 authUserId，不再传 username 调 resolveCreatedByUuid。**依赖 A 完成回填。**
- **Phase 2 权限位统一输出**：  
  - 项目详情：在 ProjectVO 或嵌套对象中返回 projectPermissions（canUpload, canSubmit, canArchive, canInvalidate, canManageMembers），由统一 PermissionService/PermissionUtil 计算。  
  - 证据详情：返回 evidencePermissions（同上或子集）。  
  - 证据列表：pageEvidence/listEvidences 为每条设置 permissions（至少 canInvalidate/canArchive/canSubmit/canUpload 中列表需要的）。  
  - **不删**现有 canInvalidate/canManageMembers 字段，可兼容过渡；前端逐步改为只读 permissions。
- **Phase 3 修不一致**：  
  - **archiveEvidence**：后端改为与作废一致（例如 checkCanInvalidate 或同一套“项目责任人”校验），与前端 canArchive 对齐。  
  - **作废证据入口**：按选定方案 A 或 B 改路由/菜单（见下节）。  
  - **上传按钮**：前端仅当 project.permissions.canUpload 时显示。

### 2.2 必须延后或条件执行

- **角色表化（sys_role / sys_user_role）**：设计已明确 V1 不做；若做需全链路改鉴权与前端，建议 V2。
- **auth_user_id 非空约束**：必须在回填完成且无遗漏后再加 NOT NULL；先可空 + 回填脚本 + 校验报表。
- **PROJECT_* 从 VALID_ROLE_CODES 移除**：若 V1 仍允许分配 PROJECT_OWNER 等（仅展示/入口），可保留；若彻底废弃需停用分配并迁移已有用户角色，建议与“作废证据入口策略”一起定稿后再动。

---

## 三、迁移步骤建议（含数据回填/脚本）

### Phase 0：冻结旧逻辑（无 DB 变更）

- 文档化：全局角色仅 SYSTEM_ADMIN/PMO/AUDITOR（及当前仍用的 PROJECT_* 仅作入口/展示，不参与项目内权限计算）。
- 代码约束：不再新增依赖 PROJECT_OWNER/EDITOR/VIEWER 做项目内权限的逻辑。

### Phase 1：修桥（DB + 后端解析）

1. **迁移脚本（Flyway）**  
   - 新增列：`ALTER TABLE sys_user ADD COLUMN auth_user_id UUID NULL;`  
   - 可选：`COMMENT ON COLUMN sys_user.auth_user_id IS '关联 auth_user.id，替代 username 弱关联';`  
   - 不回填在同一脚本中，避免阻塞部署。

2. **回填脚本（独立执行或可重复跑）**  
   - `UPDATE sys_user su SET auth_user_id = au.id FROM auth_user au WHERE su.username = au.username;`  
   - 校验：  
     - `SELECT id, username, auth_user_id FROM sys_user WHERE auth_user_id IS NULL AND is_deleted = false;` → 必须处理（创建对应 auth_user 或报表）。  
     - `SELECT auth_user_id, COUNT(*) FROM sys_user WHERE auth_user_id IS NOT NULL GROUP BY auth_user_id HAVING COUNT(*) > 1;` → 重复则需人工处理（同一 auth_user 多 sys_user 时需业务规则）。  
   - 确认无遗漏、无重复后，再考虑：  
     - `ALTER TABLE sys_user ADD CONSTRAINT uk_sys_user_auth_user_id UNIQUE (auth_user_id);`（若业务允许多 sys_user 对应同一 auth_user 则不加 UNIQUE）。

3. **后端改造**  
   - SysUser 实体、SysUserMapper（select/insert/update）支持 auth_user_id。  
   - AuthUserVO 增加 authUserId（UUID）；AuthService.getCurrentUser → 从 SysUser 取 auth_user_id 写入 VO。  
   - 所有 Controller 在需要“当前用户 UUID”时，从 request 取 AuthUserVO.getAuthUserId()，若为 null 则 403 或降级（见风险）。  
   - 逐步替换：原 `evidenceService.resolveCreatedByUuid(user.getUsername())` 改为 `user.getAuthUserId()`；EvidenceService.resolveCreatedByUuid 保留给“uploader=me”等仍用 username 的查询场景，或改为内部用 auth_user_id 反查。  
   - **不删** username 关联逻辑前，可双写：优先 auth_user_id，为 null 时再 fallback username（便于灰度）。

### Phase 2：权限位统一输出

1. **后端**  
   - 新增或收拢 PermissionService：resolveAuthUserId(请求) → UUID；getProjectRole(projectId, authUserId) → owner/editor/viewer/none；canUpload/canSubmit/canArchive/canInvalidate/canManageMembers(projectId, authUserId, roleCode) 统一计算。  
   - GET /api/projects/{id}：在 ProjectVO 或新字段 projectPermissions 中返回上述 5 个权限位。  
   - GET /api/evidence/{id}：在 EvidenceListItemVO 或 evidencePermissions 中返回同上（或子集）。  
   - pageEvidence / listEvidences：每条记录设置 permissions（至少列表页用到的位）。

2. **前端**  
   - 项目详情：上传按钮用 `project.permissions?.canUpload`（或兼容 project.canUpload）。  
   - 证据详情：提交/归档/作废用 `evidence.permissions?.canSubmit/canArchive/canInvalidate`。  
   - 成员管理入口：`project.permissions?.canManageMembers`。  
   - 列表页若有操作按钮，同样只读 permissions。

### Phase 3：修不一致

1. **archiveEvidence**：接口内改为调用与作废相同的权限校验（如 checkCanInvalidate 或统一 canArchive(projectId, authUserId, roleCode)），与前端一致。  
2. **作废证据页入口**：  
   - **方案 A**：后端增加“当前用户是否在任意项目有 canInvalidate”的标记（如 hasInvalidateAnyProject）；前端路由/菜单：SYSTEM_ADMIN 或 hasInvalidateAnyProject 才显示入口。需要聚合查询或缓存。  
   - **方案 B**：入口允许 roleCode = AUDITOR（或保留 PROJECT_AUDITOR）进入，仅只读列表；按钮仍按 canInvalidate，页面文案说明“可查看不等于可作废”。改造成本低，仅前端文案与路由角色列表可能调整。  
3. **全局角色命名**：若采用“仅 SYSTEM_ADMIN/PMO/AUDITOR”为系统级角色，需将现有 PROJECT_AUDITOR 与 AUDITOR 统一（数据与前端 roleLabel/ROLE_OPTIONS），或保留 PROJECT_AUDITOR 仅作入口展示。

### Phase 4（V2 可选）

- sys_role / sys_user_role 表化；role_code 迁移到关联表；全链路改为按 role 表鉴权。

---

## 四、最大风险点与规避方案

| 风险点 | 说明 | 规避方案 |
|--------|------|----------|
| **auth_user_id 回填失败/遗漏** | 部分 sys_user 在 auth_user 无对应 username，回填后 auth_user_id 为 NULL，登录后所有依赖 UUID 的接口无法解析当前用户。 | 回填前脚本检查：LEFT JOIN auth_user，列出无匹配的 sys_user；人工创建 auth_user 或禁用该 sys_user。回填后监控：auth_user_id IS NULL 的活跃用户数，告警。 |
| **auth_user_id 重复** | 同一 auth_user 被多个 sys_user 引用（如历史账号合并），若加 UNIQUE 会违反约束。 | 回填后查询重复；业务规则明确“一 auth_user 一 sys_user”再加 UNIQUE；否则仅索引不唯一。 |
| **双写期 fallback 行为** | 优先 auth_user_id、为 null 时 fallback username，若 username 与 auth_user 不一致会静默用错身份。 | 过渡期 fallback 时打日志；尽早修数据，缩短双写期；上线前全量校验 auth_user_id 覆盖。 |
| **归档越权** | 当前 editor/viewer 可调归档接口，与“仅责任人可归档”不一致。 | Phase 3 必须改 archiveEvidence 为与 checkCanInvalidate 同源校验，并做回归。 |
| **列表无权限位** | 列表页若将来有“作废/归档”等操作，无权限位会漏控或误显。 | Phase 2 即对 pageEvidence/listEvidences 输出 permissions；列表组件统一用 permissions 控制按钮。 |
| **作废证据入口与真实权限脱节** | 用户有入口无按钮，体验困惑。 | 采用方案 B（AUDITOR 可进只读+文案说明）成本低；方案 A 需聚合 hasInvalidateAnyProject 与前端路由改造。 |

---

## 五、必做改动的文件/模块清单（按阶段）

### 后端（Java）

| 阶段 | 模块/文件 | 改动要点 |
|------|------------|----------|
| Phase 1 | Flyway 迁移 | 新增 sys_user.auth_user_id UUID NULL；可选 UNIQUE（回填后）。 |
| Phase 1 | SysUser 实体 | 增加 authUserId (UUID)。 |
| Phase 1 | SysUserMapper.xml | select/insert/update 含 auth_user_id。 |
| Phase 1 | AuthUserVO | 增加 authUserId。 |
| Phase 1 | AuthService.getCurrentUser | 从 SysUser 取 auth_user_id 写入 VO。 |
| Phase 1 | ProjectController, EvidenceController, EvidenceVersionController | 需要 UUID 时改为 user.getAuthUserId()，null 时 403 或降级。 |
| Phase 1 | EvidenceService / ProjectService | 入参或从 request 取 authUserId，逐步替代 resolveCreatedByUuid(username)；保留 resolve 用于兼容或 me 查询。 |
| Phase 2 | PermissionUtil 或新建 PermissionService | getProjectRole(projectId, authUserId)；canUpload/canSubmit/canArchive/canInvalidate/canManageMembers 统一方法；resolveAuthUserId 从请求取 UUID。 |
| Phase 2 | ProjectService.getProjectDetail | 返回 projectPermissions（5 个位）。 |
| Phase 2 | EvidenceService.getEvidenceById | 返回 evidencePermissions（或合并进 VO）。 |
| Phase 2 | EvidenceService.pageEvidence, listEvidences | 每条设置 permissions（至少 canInvalidate/canArchive/canSubmit/canUpload 中列表所需）。 |
| Phase 2 | ProjectVO / DTO | 增加 projectPermissions 或扁平 canUpload 等；EvidenceListItemVO 增加 permissions。 |
| Phase 3 | EvidenceService.archiveEvidence | 校验改为与作废一致（项目责任人）。 |
| Phase 3 | 作废证据入口 | 按方案 A 或 B 调整（若 A：新增接口或标记 hasInvalidateAnyProject）。 |

### 前端（Vue/TS）

| 阶段 | 模块/文件 | 改动要点 |
|------|------------|----------|
| Phase 2 | ProjectDetail.vue | 上传按钮 v-if 使用 project.permissions?.canUpload（或 canUpload）。 |
| Phase 2 | EvidenceDetail.vue | 提交/归档/作废按钮用 evidence.permissions。 |
| Phase 2 | ProjectMembers.vue / 项目详情 | 成员管理入口用 project.permissions?.canManageMembers。 |
| Phase 2 | api/projects.ts, evidence 相关类型 | ProjectVO/EvidenceListItemVO 类型增加 permissions。 |
| Phase 3 | 证据列表页（若有操作按钮） | 仅用 permissions 控制。 |
| Phase 3 | router/index.ts、EvidenceHome、VoidedEvidenceList | 作废证据入口按方案 A/B 调整（角色或 hasInvalidateAnyProject）。 |
| Phase 0/3 | stores/auth.ts、Me.vue、AdminUsers | 不再用 roleCode 猜项目内权限；角色展示可保留；入口策略与后端一致。 |

### SQL / 数据

| 阶段 | 内容 |
|------|------|
| Phase 1 | Flyway：ADD COLUMN auth_user_id；回填脚本（单独或可重复）；校验查询（NULL/重复）。 |
| Phase 1 | 可选：UNIQUE(auth_user_id)、NOT NULL 在回填并修复后追加。 |

---

## 六、与现有逻辑的冲突点

1. **依赖 username 解析 UUID 的全链路**：所有传 username 进 Service 再 resolveCreatedByUuid 的调用，改为传 authUserId 或从 request 取；若有“uploader=me”等仍用 username 的查询，可保留 resolve 或改为用 auth_user_id 反查 username。  
2. **现有权限位命名**：当前 canInvalidate、canManageMembers 在 ProjectVO/EvidenceListItemVO 已存在；统一为 permissions 后，可保留旧字段一段时间并兼容前端，避免大爆炸式替换。  
3. **作废证据入口**：当前 VOIDED_EVIDENCE_ROLES = [SYSTEM_ADMIN, PROJECT_OWNER, PROJECT_AUDITOR]，与“仅系统级 SYSTEM_ADMIN/PMO/AUDITOR”目标冲突；若采用方案 B 且保留“审计入口”，可保留 PROJECT_AUDITOR 仅作入口，或统一改名为 AUDITOR 并仅用于入口。  
4. **AdminUserService.VALID_ROLE_CODES**：仍含 PROJECT_*；若 V1 冻结“不用于权限计算”但允许分配展示，可保留；若彻底去掉需同步数据与前端选项。  
5. **createProject 入参**：当前为 UUID userId（auth_user），若登录态改为 auth_user_id，创建项目时直接取 request 的 authUserId 即可，无冲突。

---

## 七、最小回归测试矩阵

| 角色/身份 | 可见项目 | 上传 | 提交 | 归档 | 作废 | 管理成员 | 作废证据页入口 | 用户管理页 |
|-----------|----------|------|------|------|------|----------|----------------|------------|
| SYSTEM_ADMIN | 全部 | 任意可见项目 ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| PMO | 全部 | 仅 ACL 内且非 viewer | 同左 | 按新规则（责任人） | 否（非责任人） | ✓ | 按方案 A/B | ✗ |
| 项目 created_by | 自己的项目 | ✓ | ✓ | ✓ | ✓ | ✓ | 按方案 A/B | ✗ |
| ACL owner | 有 ACL 的项目 | ✓ | ✓ | ✓ | ✓ | ✓ | 按方案 A/B | ✗ |
| ACL editor | 有 ACL 的项目 | ✓ | ✓ | 按新规则 | ✗ | ✗ | ✗ | ✗ |
| ACL viewer | 有 ACL 的项目 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| AUDITOR（若保留） | 按现有规则 | - | - | - | - | - | 方案 B：可进只读 | ✗ |

测试要点：每个身份在“有权限/无权限”项目各测一次；归档在 Phase 3 后仅责任人可成功；上传按钮仅 canUpload 时显示；列表页若有操作需测 permissions。

---

## 八、可执行性结论

- **可执行，但需先完成两项前提**：  
  1) **auth_user_id 回填与校验**：必须先有迁移脚本与回填结果，并处理完 NULL 与重复，再全面切换为 auth_user_id 解析；否则可先只加字段与双写，不删 username 解析。  
  2) **产品确认**：PMO 是否全放行、作废证据入口选方案 A 还是 B、是否保留 PROJECT_* 仅作展示/入口。

- **建议执行顺序**：Phase 0（文档+冻结）→ Phase 1（DB+回填+后端解析改为 auth_user_id）→ Phase 2（权限位统一输出+前端按钮）→ Phase 3（归档+作废入口策略）。  

- **不建议在未回填或未处理 NULL/重复的情况下**对“当前用户 UUID”完全移除 username 解析，否则部分用户将无法使用项目相关功能。

---

## 九、设计逐条核对摘要

| 设计条 | 当前状态 | 差异与动作 |
|--------|----------|------------|
| 1) 全局角色只用于系统级；严禁 PROJECT_* 模拟项目内权限 | 后端已满足（未用 PROJECT_* 算项目权限）；前端入口仍用 PROJECT_OWNER/AUDITOR | 冻结 PROJECT_* 含义；入口改为方案 A/B。 |
| 2) 项目内权限只看 project.created_by + auth_project_acl(role) | 已满足 | 保持；统一收拢到 getProjectRole + canXXX。 |
| 3) 前端按钮只吃后端权限位 | 部分满足：canInvalidate/canManageMembers 有；canUpload 无；列表无 permissions | Phase 2 统一 projectPermissions/evidencePermissions 及列表 permissions。 |
| 4) 后端敏感动作统一校验 | 上传/提交/作废/管理成员已校验；归档仅 checkProjectAccess | Phase 3 归档改为责任人校验。 |
| A) sys_user.auth_user_id | 不存在 | Phase 1 新增+回填+校验。 |
| B) 业务表继续用 UUID | 已满足 | 不迁移。 |
| C) 角色表化 | 未做 | V2。 |
| D) 冻结 PROJECT_* 参与权限计算 | 后端已未用 | 文档化；前端入口策略收敛。 |
| 3.1 统一 resolveAuthUserId / getProjectRole / canXXX | 无 getProjectRole；canXXX 分散在 PermissionUtil | Phase 2 收拢为 PermissionService。 |
| 3.2 动作接口统一校验 | 归档未与作废一致 | Phase 3 改 archiveEvidence。 |
| 3.3 权限位统一输出 | 零散 canInvalidate/canManageMembers；无 canUpload；列表无 | Phase 2 统一结构+列表。 |
| 3.4 作废证据入口策略 | 当前为 roleCode 控制 | 选 A 或 B，按方案改路由与后端（若 A）。 |
| 4.1 前端禁止自猜权限 | 上传无控制；提交仅状态 | Phase 2 全部改为 permissions。 |
| 4.2 路由与菜单统一 | 作废证据用 PROJECT_* | Phase 3 与方案 A/B 一致。 |

以上为基于当前代码与库表的完整评估与可执行性结论，可直接用于排期与实施决策。
