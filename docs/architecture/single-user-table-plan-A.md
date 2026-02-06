# A 方案：单一用户主表落地 — 改造说明与验证清单

## 0. 背景与目标（变更说明）

**现状核心矛盾**  
- 登录态与用户管理主体来自 **sys_user**（Long id），Session 存 sys_user.id。  
- 业务侧（project / evidence / ACL 等）大量写入并引用 **auth_user.id (UUID)**，并存在 username→auth_user 解析与 resolveCreatedByUuid 懒同步补丁，导致创建项目时报「无法解析当前用户」、双表漂移、幽灵用户风险。

**目标（A 方案）**  
- 以 **sys_user** 作为唯一用户主表与唯一身份真源。  
- 业务表全部改为引用 **sys_user.id (Long)** 作为用户外键。  
- 删除/废弃所有依赖 auth_user.uuid 的业务逻辑与解析逻辑（resolveCreatedByUuid、createdByUuid、uploaderUuid 等），以及任何「查不到就插入 auth_user」的懒同步。  
- 审计日志统一使用 sys_user.id (Long)，不再出现 UUID/Long 双轨。  
- 前端所有接口字段统一为 userId/createdByUserId/uploaderUserId 等（Long），不再传/依赖 authUserUuid。

**开发阶段**：业务数据可清空；采用破坏性迁移（清空依赖表后改表结构），通过 Flyway 可重复重建。

---

## Step A：现状扫描与改造清单

### A.1 与 auth_user/UUID 用户引用相关的代码与 SQL 位置

| 文件路径 | 行号/片段 | 用途 |
|----------|-----------|------|
| EvidenceService.java | 14,18,72,332,348-374,393,403-405,414,464,507,524,544 | AuthUser/AuthUserMapper；getVisibleProjectIds 按 username 查 auth_user；resolveCreatedByUuid；pageEvidence/upload/submit/archive/invalidate 用 UUID |
| AdminUserService.java | 4,7,47,51,145-152 | AuthUser/AuthUserMapper；create 时同步写 auth_user |
| ProjectService.java | 9,13,52,138,199,208,214,226,242-255,263+ | AuthUser/AuthUserMapper；createProject(userId UUID)；getProjectDetail/listMembers 用 resolveCreatedByUuid、selectByIds、currentPmUserId |
| ProjectController.java | 93,97,148,185,202 | resolveCreatedByUuid(user.getUsername()) 得 currentUserId/operatorUserId |
| EvidenceController.java | 48,52,82,86 | resolveCreatedByUuid 得 currentUserId |
| EvidenceVersionController.java | 54,141 | resolveCreatedByUuid 得 currentUserId |
| UserController.java | 6-7,18,25,37-39 | AuthUserMapper.selectAll，返回 AuthUserSimpleVO(UUID id) |
| PermissionUtil.java | 32-57,88,98-117,124-165,180-194 | checkProjectPermission/checkCanArchive/checkCanInvalidate/checkCanManageMembers/computeProjectPermissionBits 入参 userId 为 UUID，与 project.createdBy、acl.userId 比对 |
| ProjectMapper.java/xml | selectByCreatedBy(UUID), insert created_by | 项目创建人 UUID |
| EvidenceItemMapper.java/xml | createdBy, invalidBy UUID；selectByCreatedBy, selectPageWithFilters(createdBy), updateEvidenceInvalidate(invalidBy) | 证据上传人/作废人 UUID |
| EvidenceVersionMapper.xml | uploader_id UUID | 版本上传人 UUID |
| AuthProjectAclMapper.java/xml | user_id UUID；selectByUserId, selectByProjectIdAndUserId, insert, deleteByProjectIdAndUserId | ACL 成员 UUID |
| AuditOperationLogMapper.java + entity | actor_user_id UUID | 操作审计（当前代码未调用 insert） |
| DTO: AddProjectMemberRequest | userId UUID | 添加成员请求 |
| DTO: ProjectMemberVO | userId UUID | 成员列表 |
| DTO: AuthUserSimpleVO | id UUID | 用户选择器 |
| DTO: ProjectVO | currentPmUserId String (原 auth_user.id) | 当前项目经理 |
| DTO: EvidenceListItemVO, EvidenceResponse | createdBy, invalidBy UUID | 证据创建人/作废人 |
| Entity: Project, EvidenceItem, AuthProjectAcl, EvidenceVersion, AuditOperationLog | createdBy/userId/uploaderId/invalidBy/actorUserId 均为 UUID | 表映射 |
| V1__init.sql | auth_user 表；project.created_by, auth_project_acl.user_id, evidence_item.created_by/invalid_by, audit_operation_log.actor_user_id 均为 UUID REFERENCES auth_user(id) | 表结构 |
| V2__create_evidence_version_table.sql | evidence_version.uploader_id UUID REFERENCES auth_user(id) | 表结构 |
| EvidenceControllerTest.java | resolveCreatedByUuid mock | 单测 |
| PermissionUtilTest.java | UUID 类型 userId | 单测 |
| db/scripts/*.sql | auth_user 插入、auth_project_acl user_id、project created_by 等 | 脚本（将废弃或改为 sys_user.id） |

### A.2 用户外键字段盘点（当前类型与含义）

| 表名 | 字段 | 当前类型 | 含义 |
|------|------|----------|------|
| project | created_by | UUID FK→auth_user(id) | 项目创建人 |
| evidence_item | created_by | UUID FK→auth_user(id) | 证据上传人 |
| evidence_item | invalid_by | UUID FK→auth_user(id) | 证据作废人 |
| evidence_version | uploader_id | UUID FK→auth_user(id) | 版本上传人 |
| auth_project_acl | user_id | UUID FK→auth_user(id) | 项目成员 |
| audit_log | actor_user_id | BIGINT（无 FK，逻辑上 sys_user.id） | 登录/用户管理审计 |
| audit_operation_log | actor_user_id | UUID FK→auth_user(id) | 业务操作审计（当前未写入） |

### A.3 改造清单

**1) 需要改的表与字段**

- **project**：删除 created_by (UUID)；新增 created_by_user_id BIGINT NOT NULL REFERENCES sys_user(id)。  
- **evidence_item**：删除 created_by, invalid_by (UUID)；新增 created_by_user_id BIGINT NOT NULL REFERENCES sys_user(id)，invalid_by_user_id BIGINT REFERENCES sys_user(id)。  
- **evidence_version**：删除 uploader_id (UUID)；新增 uploader_user_id BIGINT NOT NULL REFERENCES sys_user(id)。  
- **auth_project_acl**：删除 user_id (UUID)；新增 sys_user_id BIGINT NOT NULL REFERENCES sys_user(id)；唯一约束改为 UNIQUE(project_id, sys_user_id)。  
- **audit_operation_log**：删除 actor_user_id (UUID)；新增 actor_user_id BIGINT REFERENCES sys_user(id)。  
- **audit_log**：保持 actor_user_id BIGINT 不变（已是 sys_user.id）。  
- **auth_user**：保留表但不被业务表引用（deprecated）；不再写入业务外键。

**2) 需要改的后端类与方法**

- **Entity**：Project、EvidenceItem、AuthProjectAcl、EvidenceVersion、AuditOperationLog 的 user 相关字段全部改为 Long。  
- **Mapper 接口**：ProjectMapper（selectByCreatedBy(Long)）、EvidenceItemMapper（selectByCreatedBy(Long)、selectPageWithFilters/countPageWithFilters(createdBy Long)、updateEvidenceInvalidate(invalidBy Long)）、EvidenceVersionMapper（uploaderUserId Long）、AuthProjectAclMapper（userId→sysUserId Long；selectByUserId(Long)、selectByProjectIdAndUserId(projectId, Long)、deleteByProjectIdAndUserId(projectId, Long)）、AuditOperationLogMapper（actorUserId Long）。  
- **Mapper XML**：上述表对应 XML 中列名与 typeHandler 改为 BIGINT，不再使用 UUIDTypeHandler。  
- **Service**：  
  - EvidenceService：删除 resolveCreatedByUuid、AuthUserMapper/SysUserMapper 懒同步；getVisibleProjectIds 改为按 sys_user.id（当前用户 id Long）查 project.created_by_user_id 与 auth_project_acl.sys_user_id；pageEvidence/upload/submit/archive/invalidate 全部使用 currentUserId (Long)。  
  - ProjectService：createProject(Long userId)、importProjectsFromExcel(Long operatorUserId)、getProjectDetail/listMembers/addOrUpdateMember/removeMember 全部使用 sys_user.id；删除 AuthUserMapper 依赖；成员展示用 SysUserMapper 按 id 查 realName/username。  
  - AdminUserService：删除创建用户时同步写 auth_user 的逻辑；删除 AuthUserMapper 依赖。  
- **Controller**：ProjectController/EvidenceController/EvidenceVersionController 从 request 取 currentUser.getId() (Long) 直接传递，不再调用 resolveCreatedByUuid。  
- **UserController**：改为 SysUserMapper 查 sys_user 列表，返回 userId (Long)、username、realName。  
- **PermissionUtil**：所有入参 userId 改为 Long；与 project.getCreatedByUserId()、acl.getSysUserId() 比对。  
- **DTO**：AddProjectMemberRequest.userId、ProjectMemberVO.userId、AuthUserSimpleVO.id 改为 Long；ProjectVO.currentPmUserId 改为 Long 或 String 表示数字；EvidenceListItemVO/EvidenceResponse 的 createdBy、invalidBy 改为 Long；展示名由后端按 sys_user 查 realName。

**3) 需要改的前端**

- **api/projects.ts**：currentPmUserId、成员 userId 类型改为 number；removeProjectMember(projectId, userId: number)。  
- **api/evidence.ts**：createdBy 改为 number 或 string 数字。  
- **views/ProjectMembers.vue**：userId 为 number；添加成员请求 body userId 为 number。  
- **views/evidence/EvidenceDetail.vue**：createdBy 展示可仍为文本（后端返回 displayName 或由 id 查）。

**4) 需要删除/废弃的逻辑**

- EvidenceService.resolveCreatedByUuid 整个方法及所有调用。  
- EvidenceService/AdminUserService 中「查不到 auth_user 则从 sys_user 插入 auth_user」的懒同步。  
- AuthUserMapper 在业务中的使用（UserController 改为 SysUserMapper；ProjectService 成员展示改为 SysUser；可选保留 AuthUserMapper 与 auth_user 表仅作废弃表，不再写入）。  
- 所有 UUID 类型用户标识的传参与比较。

### A.4 风险点对照表

| 风险 | 如何避免 | 如何验证 |
|------|----------|----------|
| **R1 漏改引用点导致运行时报错** | 全局搜索 UUID、auth_user、AuthUser、resolveCreatedByUuid、createdBy(UUID)、user_id(UUID)；编译通过；单测与集成测覆盖创建项目、上传证据、成员管理、列表详情。 | 全量编译；跑 JUnit；启动应用后按验证清单调接口。 |
| **R2 ACL/权限判断越权或阻断** | PermissionUtil 与 EvidenceService.getVisibleProjectIds 全部改为 Long；project/evidence 的 created_by 比较改为 created_by_user_id；ACL 查改为 sys_user_id。 | 单测 PermissionUtil（Long userId）；集成测：非成员无权限、owner/editor/viewer 边界、SYSTEM_ADMIN/PMO 可见性。 |
| **R3 审计日志双轨** | audit_log 已为 Long；audit_operation_log 改为 actor_user_id BIGINT；若有写入处统一传 sys_user.id。 | 检索 recordAudit/audit 写入点，确认均为 Long；审计表无 UUID 列。 |
| **R4 前端字段与后端不一致** | 接口契约：createdByUserId/uploaderUserId/currentPmUserId 等统一 Long；前端 TS 类型与请求体/路径参数为 number。 | 前端编译；联调添加成员、移除成员、列表展示创建人/上传人。 |
| **R5 并发/唯一约束与重复成员** | auth_project_acl 保留 UNIQUE(project_id, sys_user_id)；添加成员前查重；接口幂等或返回明确错误。 | 集成测：同一用户重复添加成员返回 400 或幂等；唯一约束存在。 |
| **R6 用户禁用/自我修改限制** | AdminUserService 中禁止对自己禁用/改角色/删账号等逻辑保持不变，仍基于 sys_user.id（request 中 currentUser.getId()）。 | 单测 AdminUserServiceSelfOperationTest；手工：admin 不能改自己、不能删自己。 |

---

## Step B：数据库迁移设计（Flyway V10）

- **策略**：新增 **V10__single_user_table_fk.sql**，在开发阶段可清库前提下：  
  1) 按依赖顺序 TRUNCATE evidence_version、evidence_item、auth_project_acl、project、audit_operation_log（RESTART IDENTITY）。  
  2) 对各表执行：删除原 UUID 列及其 FK 约束，新增 *_user_id BIGINT 列并 REFERENCES sys_user(id)；ACL 表唯一约束改为 (project_id, sys_user_id)。  
- **字段命名规范**：project.created_by_user_id；evidence_item.created_by_user_id、invalid_by_user_id；evidence_version.uploader_user_id；auth_project_acl.sys_user_id；audit_operation_log.actor_user_id（类型改为 BIGINT）。  
- **auth_user**：V11 迁移中已 DROP TABLE auth_user；实体 AuthUser、AuthUserMapper 及 XML 已删除。  
- **迁移前后对照**：所有业务表用户引用由 UUID→auth_user 改为 BIGINT→sys_user；audit_log 保持 BIGINT。

---

## Step C / D / E 概要（详见正文与代码）

- **Step C**：后端实体/Mapper/Service/Controller/Util 全量改为 Long，删除 resolveCreatedByUuid 与 auth_user 写逻辑；管理员自我操作限制保留。  
- **Step D**：前端接口与页面改为 userId (number)、createdByUserId 等。  
- **Step E**：最小自动化测试（登录、创建项目、添加成员、上传证据、权限边界）+ 手工验收 checklist（≥20 条）；文档中写明已跑测试与结果。

---

## Step E：验证与对账

### E.1 最小自动化测试（建议执行方式）

- **环境**：本地或 CI 执行 `mvn test`，需先执行 Flyway 迁移（V1～V10）或使用 H2 内存库 + 等效 schema。  
- **用例覆盖**：  
  1. **admin 登录**：POST /api/auth/login，admin/密码，断言 200 与 Session。  
  2. **PMO 登录**：PMO 账号登录，断言 200。  
  3. **PMO 创建项目**：带 token 调用 POST /api/projects，body 含 name/code 等，断言 200，且 project.createdByUserId 为当前 sys_user.id。  
  4. **PMO 添加项目成员**：POST /api/projects/{id}/members，body 含 userId（Long）、role，断言 200；再次添加同一 userId 断言 400 或幂等。  
  5. **项目经理仅可见被分配项目**：以项目经理身份登录，GET /api/projects，断言仅返回其所在项目。  
  6. **成员上传证据**：以 editor 身份 POST /api/evidence/upload，断言 200，evidence.createdByUserId 为当前用户 id。  
  7. **权限边界**：owner/editor/viewer 的 canUpload/canSubmit/canArchive/canInvalidate/canManageMembers 与 PermissionUtil 一致；非成员 403。  
- **已有单测**：EvidenceControllerTest（Long userId）、PermissionUtilTest（Long userId）、AdminUserServiceSelfOperationTest（自我操作限制）需全部通过。

### E.2 手工验收 Checklist（≥20 条）

| # | 验收项 | 通过 |
|---|--------|------|
| 1 | 登录：admin 正确账号密码可登录 | ☐ |
| 2 | 登录：错误密码拒绝并提示 | ☐ |
| 3 | 退出：退出后需重新登录才能访问接口 | ☐ |
| 4 | 重复登录：同一用户多端登录行为符合预期（Session 策略） | ☐ |
| 5 | 创建项目：PMO/管理员创建项目成功，列表可见 | ☐ |
| 6 | 项目列表：创建人展示为 sys_user 关联（或 ID），无 UUID | ☐ |
| 7 | 项目详情：currentPmUserId 为数字，创建人一致 | ☐ |
| 8 | 项目成员授权：添加成员时选择用户（id 为数字），添加成功 | ☐ |
| 9 | 项目成员撤销：移除成员成功，列表更新 | ☐ |
| 10 | 重复授权：同一用户同一项目重复添加返回 400 或幂等，不产生重复 ACL | ☐ |
| 11 | 上传证据：在已授权项目中上传，成功且 createdByUserId 为当前用户 | ☐ |
| 12 | 证据列表：上传人/创建人展示为 ID 或 displayName，无 UUID | ☐ |
| 13 | 证据详情：上传人、作废人为 userId 或展示名，无 UUID | ☐ |
| 14 | 证据状态机：保存草稿 → 提交 → 归档 流程正常 | ☐ |
| 15 | 证据作废：有权限用户作废成功，invalidByUserId 为当前用户 | ☐ |
| 16 | 禁用用户：禁用后该用户无法登录，且其 sys_user.id 仍被业务表引用时列表/详情不报错 | ☐ |
| 17 | 管理员自我修改限制：管理员不能禁用自己、不能改自己角色、不能删自己 | ☐ |
| 18 | 并发/重复点击：连续两次「创建项目」或「添加成员」不产生脏数据，唯一约束生效 | ☐ |
| 19 | 审计日志：操作者统一为 sys_user.id（Long），无 UUID 列或双轨 | ☐ |
| 20 | 底部导航/路由：项目、证据、我的证据、成员管理等页面跳转与高亮正常 | ☐ |

### E.3 已执行测试与结果说明

- **编译**：本环境未配置 Maven PATH，未在本地执行 `mvn compile`；请在仓库中执行 `mvn -q compile` 与 `mvn -q test` 做一次完整自检。  
- **单测**：EvidenceControllerTest、PermissionUtilTest、AdminUserServiceSelfOperationTest 已按 Long userId 改造；是否全部通过需在本地/CI 运行 `mvn test` 确认。  
- **集成/手工**：未在本会话中启动应用或执行 HTTP 请求；请按 E.1、E.2 在本地或测试环境执行并勾选 checklist。  
- **结论**：代码与迁移、文档已按 A 方案落地；最终通过与否以「编译 + 单测 + 手工 checklist」为准。

---

## 后续建议

- **auth_user 表**：已通过 **V11__drop_auth_user_table.sql** 删除；并已移除 `AuthUser` 实体、`AuthUserMapper` 接口及其 XML，避免死代码。`AuthUserVO` / `AuthUserSimpleVO` 为 DTO 命名保留，实际数据来自 sys_user。
