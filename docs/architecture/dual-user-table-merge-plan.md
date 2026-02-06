# 双用户表合一：架构审计与可落地方案

> 本文档为**纯分析与方案**，不修改任何代码、表结构、Flyway 历史或执行任何迁移/清库脚本。  
> 项目：项目交付证据管理系统（evidence-manager）。

---

## 一、结论摘要

- **推荐路径**：**C. 过渡方案** — 先引入 `sys_user.auth_user_id`（或独立映射表 `user_identity_map`）作为“单一真源”，登录态与业务侧统一通过该关联解析 UUID，保留 auth_user 与业务表外键不变；待数据与调用稳定后再择机演进到**以 sys_user 为唯一主表**（路径 A），业务表逐步从引用 auth_user(UUID) 改为引用 sys_user.id 或 sys_user 上的统一业务 ID。
- **关键理由**：① 当前业务表（project、evidence_item、auth_project_acl、evidence_version、audit_operation_log）全部以 **auth_user.id(UUID)** 为外键，且 FK 约束已存在，若立刻以 sys_user 为主表需改所有表结构，风险与回滚成本高；② 登录与审计链路已完全基于 **sys_user**（Session 存 sys_user.id，audit_log 存 actor_user_id BIGINT），若立刻以 auth_user 为主表需迁密码、角色、审计等多处，且 auth_user 无 password_hash/role_code；③ 过渡方案在不改业务表外键的前提下，用“桥接字段”或映射表解决“当前用户 UUID”解析问题，并消除 username 弱关联与懒同步带来的数据漂移与幽灵用户风险。
- **主要风险**：回填阶段存在 sys_user 无对应 auth_user 导致 auth_user_id 为 NULL；双写/fallback 期若逻辑不当可能静默用错身份；历史数据迁移时需保证 created_by/uploader/ACL 等展示正确。
- **下一步行动**：① 确认产品对“用户名是否允许修改、大小写规范”的约束；② 执行现状盘点中的“回填前”检查（无匹配 auth_user 的 sys_user 列表），补齐 auth_user 或禁用账号；③ 按选定路径制定 Phase 1 的 Flyway 与回填脚本（仅设计，不在此文档执行）。

---

## 二、现状盘点

### 2.1 与用户/认证/权限/ACL/审计相关的表清单

| 表名 | 用途推断 | 主键 | 关键字段（username/uuid/外键/状态） | 备注 |
|------|----------|------|-------------------------------------|------|
| **sys_user** | 登录账号、全局角色、启用状态 | id (BIGSERIAL) | username UNIQUE, password_hash, real_name, role_code, enabled, is_deleted, last_login_* | V4 创建；无 auth_user 关联字段 |
| **auth_user** | 业务侧用户标识，项目/证据创建人、ACL 主体 | id (UUID) | username UNIQUE, display_name, email, is_active, created_at | V1 创建；被 project/evidence_item/auth_project_acl/evidence_version/audit_operation_log 引用 |
| **project** | 项目 | id (BIGSERIAL) | code UNIQUE, name, **created_by UUID REFERENCES auth_user(id)** | 创建人写 auth_user.id |
| **auth_project_acl** | 项目成员与角色 | id (BIGSERIAL) | project_id FK→project, **user_id UUID REFERENCES auth_user(id)**, role IN (owner,editor,viewer), UNIQUE(project_id,user_id) | 成员为 auth_user.id |
| **evidence_item** | 证据元数据 | id (BIGSERIAL) | project_id, **created_by UUID**, **invalid_by UUID** REFERENCES auth_user(id), status | 上传人/作废人均为 auth_user.id |
| **evidence_version** | 证据版本 | id (BIGSERIAL) | evidence_id, project_id, **uploader_id UUID REFERENCES auth_user(id)**, version_no, ... | 上传人为 auth_user.id |
| **audit_log** | 登录/登出/用户管理/操作审计 | id (BIGSERIAL) | **actor_user_id BIGINT**（无 FK，逻辑上 sys_user.id）, action, target_type, target_id, success, ip, project_id, before_data, after_data | V4；登录/登出/USER_CREATE 等用 sys_user.id |
| **audit_operation_log** | 业务操作审计（上传/作废等） | id (BIGSERIAL) | **actor_user_id UUID NOT NULL REFERENCES auth_user(id)** | V1；代码中 Mapper 存在，当前未检索到 insert 调用点，可能遗留 |

说明：角色/权限未单独表化，全局角色存于 sys_user.role_code；项目内角色存于 auth_project_acl.role。

### 2.2 代码中关键词/实体使用点归类

#### sys_user / SysUser

| 文件路径 | 方法/位置 | 作用 |
|----------|------------|------|
| AuthService.java | login(), getCurrentUser() | 按 username 查 sys_user 校验密码；按 sys_user.id 查用户并转 AuthUserVO |
| AuthService.java | toAuthUserVO(SysUser) | 将 SysUser 转为 AuthUserVO（id=sys_user.id, username, realName, roleCode, enabled） |
| AuthInterceptor.java | preHandle() | 从 Session 取 LOGIN_USER_ID（sys_user.id），用 sysUserMapper.selectById 校验并设置 CURRENT_USER |
| AdminUserService.java | create(), page(), updateEnabled(), update(), resetPassword(), delete() | 用户管理 CRUD 全部基于 SysUser/SysUserMapper；create 内同步写 auth_user |
| EvidenceService.java | resolveCreatedByUuid() | 若 auth_user 无则用 sysUserMapper.selectByUsername 查 sys_user 并懒同步插入 auth_user |
| SysUserMapper.java / SysUserMapper.xml | 全表 CRUD、countByUsername、pageQuery | 读写 sys_user 表 |
| PermissionUtil.java | 注释 | 注明 roleCode 来自 sys_user.role_code |
| db/migration/V4__init_user_and_audit.sql | CREATE TABLE, INSERT admin | 创建 sys_user 及初始 admin |
| db/scripts/*.sql, docs/*.md | 多处 | 脚本与文档中对 sys_user 的引用 |

#### auth_user / AuthUser

| 文件路径 | 方法/位置 | 作用 |
|----------|------------|------|
| EvidenceService.java | getVisibleProjectIds(), resolveCreatedByUuid(), pageEvidence(), uploadEvidence(), submitEvidence(), archiveEvidence(), invalidateEvidence() | 用 authUserMapper 按 username 查 UUID；可见项目=created_by 或 auth_project_acl 中 user_id=auth_user.id |
| ProjectService.java | createProject(), getProjectDetail(), listMembers(), addOrUpdateMember(), removeMember() | 创建项目/ACL 用 auth_user.id；成员列表/PM 展示用 authUserMapper.selectById/selectByIds |
| ProjectController.java | createProject(), addMember(), removeMember(), ... | 通过 evidenceService.resolveCreatedByUuid(user.getUsername()) 得到 UUID 再调 ProjectService |
| EvidenceController.java | listEvidences(), getEvidenceById() | resolveCreatedByUuid(user.getUsername()) 得到当前用户 UUID |
| EvidenceVersionController.java | 多个接口 | 同上，取当前用户 UUID |
| PermissionUtil.java | checkProjectPermission(), checkCanArchive(), checkCanInvalidate(), checkCanManageMembers(), computeProjectPermissionBits() | 入参 userId 为 auth_user.id(UUID)，与 project.created_by、auth_project_acl.user_id 比较 |
| UserController.java | listUsers() | authUserMapper.selectAll()，供项目成员选择器（仅展示 auth_user 列表） |
| AdminUserService.java | create() | 插入 sys_user 后同步插入 auth_user（username, display_name, email, is_active） |
| AuthUserMapper.java / AuthUserMapper.xml | 全表 CRUD、selectByUsername、selectByIds | 读写 auth_user |
| V1__init.sql、V2、mapper XML | 表定义与 SQL | project/evidence_item/auth_project_acl/evidence_version/audit_operation_log 的 created_by/user_id/uploader_id/actor_user_id 均为 UUID→auth_user(id) |

#### created_by / createdByUuid / resolveCreatedByUuid

| 文件路径 | 方法/位置 | 作用 |
|----------|------------|------|
| ProjectController.java | createProject() L97 | resolveCreatedByUuid(user.getUsername()) → 为 null 则 403「无法解析当前用户」 |
| ProjectController.java | addMember(), removeMember() | resolveCreatedByUuid 得到 operatorUserId |
| EvidenceController.java | listEvidences(), getEvidenceById() | resolveCreatedByUuid(user.getUsername()) 得到 currentUserId |
| EvidenceVersionController.java | invalidateEvidence() | resolveCreatedByUuid(user.getUsername()) |
| EvidenceService.java | resolveCreatedByUuid() L352-374 | 先 auth_user 按 username；无则 sys_user 按 username 后 insert auth_user（懒同步），DuplicateKeyException 时重查 |
| EvidenceService.java | getVisibleProjectIds() L332 | authUserMapper.selectByUsername(username) 得 UUID，再查 project.created_by、auth_project_acl.user_id |
| EvidenceService.java | pageEvidence(), uploadEvidence(), submitEvidence(), archiveEvidence(), invalidateEvidence() | 内部调用 resolveCreatedByUuid(username) 得到 UUID 用于权限/写入 |
| ProjectService.java | getProjectDetail(), listMembers() | evidenceService.resolveCreatedByUuid(username) 得到 currentAuthUserId |
| ProjectMapper.xml / EvidenceItemMapper.xml | INSERT/SELECT | project.created_by, evidence_item.created_by/invalid_by 为 UUID 字段 |

#### uploader / owner / editor / viewer / ACL / project member

| 文件路径 | 方法/位置 | 作用 |
|----------|------------|------|
| EvidenceService.java | pageEvidence() L393 | uploader=me 时 createdByUuid=resolveCreatedByUuid(username)，过滤“我上传”的证据 |
| EvidenceVersionController.java | listEvidences() 参数 | uploader 查询参数 |
| ProjectService.java | ROLE_OWNER, ACL_ROLES, createProject(), addOrUpdateMember(), removeMember(), resolveCurrentPmUserId(), listMembers() | ACL 增删改查；owner/editor/viewer；成员列表来自 auth_project_acl + auth_user |
| PermissionUtil.java | checkProjectPermission(), checkCanArchive(), checkCanInvalidate(), checkCanManageMembers(), computeProjectPermissionBits() | 基于 project.created_by 与 auth_project_acl(projectId, userId) 判断 owner/editor/viewer |
| AddProjectMemberRequest.java | role | owner/editor/viewer |
| ProjectMemberVO / ProjectVO | 成员与当前 PM | currentPmUserId 为 auth_user.id |

#### token / login / Jwt / principal / SecurityContext

| 文件路径 | 方法/位置 | 作用 |
|----------|------------|------|
| AuthService.java | SESSION_LOGIN_USER_ID = "LOGIN_USER_ID", login(), logout(), getCurrentUser() | Session 存 sys_user.id(Long)；无 JWT；无 Spring Security principal |
| AuthInterceptor.java | preHandle() | 从 Session 取 LOGIN_USER_ID，校验 sys_user 存在且 enabled、未删除，设置 REQUEST_CURRENT_USER 为 AuthUserVO |
| AuthController.java | /login, /logout, /me | 登录写 Session；登出 invalidate；/me 从 request 取 CURRENT_USER |
| 全项目 | — | 无 Jwt、无 principal、无 SecurityContext；认证即 Session + 拦截器 |

### 2.3 登录认证链路（当前用户主体来自哪张表）

1. **登录**：AuthController.login → AuthService.login(body.username, body.password) → SysUserMapper.selectByUsername(username) 查 **sys_user** → 校验密码、enabled、is_deleted → session.setAttribute(LOGIN_USER_ID, **user.getId()**)（即 **sys_user.id, Long**）→ 更新 last_login_at/ip → 返回 toAuthUserVO(sysUser)（id, username, realName, roleCode, enabled）。
2. **后续请求**：AuthInterceptor.preHandle → session.getAttribute(LOGIN_USER_ID) 得到 **Long userId** → SysUserMapper.selectById(userId) 查 **sys_user** → 校验存在且 enabled、未删除 → request.setAttribute(REQUEST_CURRENT_USER, **AuthUserVO**(id=sys_user.id, username, realName, roleCode, enabled))。
3. **结论**：当前用户主体 100% 来自 **sys_user**；Session 中仅存 **sys_user.id (Long)**；**AuthUserVO 中无 UUID/auth_user_id**。业务侧需要“当前用户 UUID”时，全部通过 **user.getUsername() → resolveCreatedByUuid(username)** 再查 **auth_user** 得到。

### 2.4 业务写入链路（写入了哪个用户标识到哪些字段）

| 写操作 | 用户标识来源 | 写入的表与字段 | 代码位置 |
|--------|--------------|----------------|----------|
| 创建项目 | resolveCreatedByUuid(username) → UUID | project.created_by, auth_project_acl.user_id + role=owner | ProjectController.createProject → ProjectService.createProject(userId UUID) |
| 上传证据 | resolveCreatedByUuid(username) → UUID | evidence_item.created_by；evidence_version.uploader_id | EvidenceController → EvidenceService.uploadEvidence |
| 提交/归档/作废证据 | resolveCreatedByUuid(username) → UUID | evidence_item 的 status/updated_at；作废时 invalid_by, invalid_at | EvidenceService.submitEvidence/archiveEvidence/invalidateEvidence |
| 添加/调整项目成员 | resolveCreatedByUuid(username) → UUID；body.userId 为 UUID | auth_project_acl.project_id, user_id(UUID), role | ProjectController → ProjectService.addOrUpdateMember |
| 审计（登录/登出/用户管理） | sys_user.id (Long) | audit_log.actor_user_id (BIGINT) | AuthService.recordAudit(request, …, actorUserId, …) |

结论：**业务表一律写入 auth_user.id (UUID)**；**audit_log 写入 sys_user.id (Long)**。两套 ID 通过 username 在应用层桥接。

### 2.5 当前已存在的补丁逻辑及副作用评估

| 补丁逻辑 | 位置 | 副作用评估 |
|----------|------|------------|
| 管理员创建用户时同步写 auth_user | AdminUserService.create() 在 sysUserMapper.insert 后 authUserMapper.insert | **数据漂移**：auth_user 的 display_name/email/is_active 与 sys_user 后续修改不同步；**唯一约束**：username 一致，一般无冲突。 |
| resolveCreatedByUuid 懒同步（auth_user 无则从 sys_user 插入） | EvidenceService.resolveCreatedByUuid() | **幽灵用户**：仅登录过、从未在“用户管理”创建的用户若被其他途径得到 username，可能先被懒同步插入 auth_user，与“仅管理员创建”策略不一致；**并发**：DuplicateKeyException 后重查，可接受；**信息不同步**：懒同步记录来自当时 sys_user 快照，后续 sys_user 变更不会回写 auth_user。 |
| 可选脚本 seeds_auth_user_after_reset.sql / admin_recover | db/scripts | 人工为固定 username 补齐 auth_user，与 AdminUserService 双写、懒同步并存，易造成“谁是真源”混乱。 |

---

## 三、问题与风险清单（具体条目）

| # | 问题/风险 | 触发场景 | 影响范围 | 严重等级 | 现状是否已有防护 |
|---|------------|----------|----------|----------|------------------|
| 1 | **双表一致性**：sys_user 与 auth_user 同人两处维护，display_name/email/enabled 不一致 | 管理员只改 sys_user.real_name 或 enabled，auth_user 未改 | 成员列表/PM 展示用 auth_user.display_name；业务侧无 enabled 校验，仅登录用 sys_user | 中 | 无；仅依赖人工或脚本双写 |
| 2 | **用户名作为关联键**：全链路用 username 解析 UUID，username 若可改则断链 | 将来若支持“修改登录名”或批量导入导致 username 不一致 | 所有 resolveCreatedByUuid、getVisibleProjectIds、ACL 展示 | 高 | 无；且 AuthUserVO 无 authUserId，无法脱离 username |
| 3 | **权限与可见性**：getVisibleProjectIds 依赖 auth_user 存在 | 仅存在 sys_user 的用户（未同步或懒同步未触发）访问项目列表 | 列表为空或仅部分可见；创建项目前 resolve 为 null 则 403 | 高 | 懒同步可缓解创建项目；列表仍依赖 auth_user 先存在 |
| 4 | **认证与业务耦合**：登录与业务分别依赖 sys_user 与 auth_user，无统一 ID | 任一张表缺数据或 username 不一致 | 登录成功但“无法解析当前用户”、无法创建项目、无法看到应有项目 | 高 | 双写+懒同步部分缓解，未从根上统一身份 |
| 5 | **历史数据迁移**：若未来合并表，project/evidence_item 等 created_by 为 UUID | 迁移到以 sys_user 为主表时需映射 UUID→新主键 | 所有业务表外键与展示逻辑 | 高 | 未做；依赖方案中的迁移路径 |
| 6 | **回滚困难**：业务表外键指向 auth_user，若删除或合并 auth_user | 误删或迁移错误 | 外键约束失败或历史创建人/上传人错乱 | 高 | 需分阶段、可回滚方案 |
| 7 | **审计双轨**：audit_log 用 sys_user.id(BIGINT)，audit_operation_log 用 auth_user.id(UUID) | 统一审计查询需关联两套 ID | 运营/合规审计需同时查两表并按 username 对账 | 中 | 无统一视图或文档 |
| 8 | **禁用用户**：仅 sys_user.enabled，auth_user.is_active 未参与登录与业务校验 | 禁用后登录被拦；auth_user 仍可能被选为成员、展示在列表 | 成员选择器仍可选出已禁用用户；历史 ACL 仍有效直到被移除 | 中 | 无；若以 auth_user 为主表需统一状态 |
| 9 | **并发重复插入 auth_user** | 同一 username 首次创建项目/证据时多请求同时懒同步 | 唯一约束冲突，catch 后重查，得到正确 UUID | 低 | 有；DuplicateKeyException 处理 |
| 10 | **成员选择器数据源**：/api/users 仅查 auth_user | 新加用户仅写 sys_user 时选择器无此人（未执行种子脚本或未触发懒同步） | 无法被选为项目成员 | 中 | 管理员创建时已双写；历史或脚本环境仍可能缺 |

---

## 四、三种合并路径详细方案（仅方案，不改代码）

### 路径 A：以 sys_user 为唯一主表，业务表统一引用 sys_user.id（或新增统一业务 ID）

- **目标数据模型**：  
  - 唯一用户主表：**sys_user**。主键 id (BIGINT)。  
  - 可选：在 sys_user 上新增 **business_id UUID UNIQUE**（或保持 id 为唯一业务引用），用于替代原 auth_user.id 的引用，避免业务表主键类型全改为 BIGINT。  
  - 业务表：project.created_by、evidence_item.created_by/invalid_by、auth_project_acl.user_id、evidence_version.uploader_id、audit_operation_log.actor_user_id 由“引用 auth_user(id)”改为“引用 sys_user.business_id”或 sys_user.id（若统一为 BIGINT，则全部改为 BIGINT，且需删 FK 再建 FK）。  
  - auth_user 表：逐步停写、只读，最终下线或保留为历史视图。

- **迁移策略**：  
  - 阶段 1：sys_user 增加 business_id UUID UNIQUE NULL；回填：对每个 sys_user 若存在 auth_user 同 username 则 business_id = auth_user.id，否则 gen_random_uuid()。  
  - 阶段 2：业务表增加 created_by_sys_id BIGINT NULL（或保留 created_by 为 UUID 改为引用 sys_user.business_id）；双写：写业务时同时写 created_by 与 created_by_sys_id（或仅写 business_id）；回填历史数据 created_by_sys_id 通过 auth_user→username→sys_user 映射。  
  - 阶段 3：读路径切换为按 sys_user 解析（从 Session 取 sys_user.id 或 business_id）；写路径统一写 sys_user.business_id 或 sys_user.id。  
  - 阶段 4：业务表 FK 与 NOT NULL 改为仅依赖 sys_user；下线 resolveCreatedByUuid 及 auth_user 写入口。

- **约束与规范**：username 在 sys_user 唯一且作为登录名，若允许修改需同步更新所有依赖 username 的缓存/展示；建议业务侧统一用 business_id 或 sys_user.id，不再用 username 做关联键。

- **代码改造点清单**（仅列出）：AuthService/AuthInterceptor 保持 Session 存 sys_user.id；AuthUserVO 增加 businessId(UUID) 或仅用 id(Long)；ProjectController/EvidenceController/EvidenceVersionController/ProjectService/EvidenceService 所有 resolveCreatedByUuid(user.getUsername()) 改为 user.getBusinessId() 或 user.getId()（若业务表改 BIGINT）；PermissionUtil/EvidenceService.getVisibleProjectIds 改为按 sys_user.id 或 business_id 查 project/auth_project_acl；UserController 成员列表改为 SysUserMapper（或 sys_user+角色视图）；AdminUserService.create 不再写 auth_user；EvidenceService 删除懒同步；Mapper/XML：project、evidence_item、auth_project_acl、evidence_version、audit_operation_log 的 created_by/user_id/uploader_id/actor_user_id 改为引用 sys_user；Flyway 新增列、回填、改 FK。

- **回滚**：每阶段保留“双写+双读 fallback”；回滚时切回读 auth_user/created_by，停写 sys_user 侧新字段。

- **工作量**：P0：Flyway 与回填、AuthUserVO 与解析链路；P1：业务表双写与读切 sys_user、PermissionUtil/可见性；P2：下线 auth_user 写、成员接口与前端、审计统一。

---

### 路径 B：以 auth_user 为唯一主表，将 sys_user 合并进 auth_user

- **目标数据模型**：  
  - 唯一用户主表：**auth_user**。主键 id (UUID)。  
  - auth_user 增加：password_hash, role_code, enabled, is_deleted, last_login_at, last_login_ip, created_at/updated_at（与现有 display_name, email, is_active 等并存）。  
  - 登录与审计：Session 存 auth_user.id (UUID)；audit_log.actor_user_id 改为 UUID 并 REFERENCES auth_user(id)。  
  - sys_user 表：只读或视图，最终下线。

- **迁移策略**：  
  - 阶段 1：auth_user 增加 password_hash、role_code、enabled、is_deleted、last_login_*、updated_at 等列；数据迁移：按 username 把 sys_user 对应行数据合并到 auth_user（若 auth_user 已存在则 UPDATE，否则 INSERT auth_user 含密码等）。  
  - 阶段 2：登录改为按 auth_user 校验（username+password_hash）；Session 存 auth_user.id (UUID)；AuthUserVO 以 UUID 为主；AuthInterceptor 按 UUID 查 auth_user。  
  - 阶段 3：audit_log 增加 actor_auth_user_id UUID 或直接改 actor_user_id 为 UUID 并迁移历史（需映射原 BIGINT→UUID）；AdminUserService 改为只写 auth_user。  
  - 阶段 4：下线 sys_user 表或保留为视图。

- **约束与规范**：auth_user.username 唯一且作为登录名；密码、角色、启用状态唯一存放在 auth_user。

- **何时可行/不可行**：可行条件为“接受 auth_user 承载密码与登录态、且 audit_log 历史可迁移或兼容”。不可行点：现有 audit_log 大量 BIGINT 且无 FK，若保留双轨则审计仍双表；且当前 Session/拦截器全基于 Long，改造面大。

- **代码改造点清单**：AuthService 改为 AuthUserMapper 查用户与密码；Session 存 UUID；AuthInterceptor 按 UUID 查 auth_user 设 VO；AdminUserService 只操作 auth_user；AuditLog 实体与 Mapper 的 actor_user_id 改为 UUID 或双字段；所有 Controller/Service 当前取 user.getId()(Long) 改为 UUID；Flyway：auth_user 加列、数据迁移、audit_log 改型或加列。

- **回滚**：保留 sys_user 至阶段 3 结束；回滚时登录与 Session 切回 sys_user。

- **工作量**：P0：auth_user 表扩展与数据迁移、登录/Session/拦截器全改；P1：审计与用户管理；P2：下线 sys_user。

---

### 路径 C（推荐）：过渡方案 — 映射表或 sys_user.auth_user_id，单一真源 + 兼容

- **目标数据模型**：  
  - **不改变业务表外键**：project、evidence_item、auth_project_acl、evidence_version、audit_operation_log 继续引用 auth_user(id) UUID。  
  - **方案 C1（推荐）**：sys_user 增加 **auth_user_id UUID NULL REFERENCES auth_user(id)**；唯一约束可选（一 auth_user 对应一 sys_user）。回填：UPDATE sys_user su SET auth_user_id = au.id FROM auth_user au WHERE su.username = au.username。  
  - **方案 C2**：独立表 **user_identity_map**（id, sys_user_id BIGINT UNIQUE, auth_user_id UUID UNIQUE），替代在 sys_user 上增列；登录态解析时通过 sys_user_id 查 auth_user_id。  
  - 登录与当前用户：Session 仍存 sys_user.id (Long)；AuthUserVO 增加 **authUserId (UUID)**；getCurrentUser 从 SysUser 取 auth_user_id 写入 VO；所有需“当前用户 UUID”的接口改为 **user.getAuthUserId()**，为 null 时 fallback resolveCreatedByUuid(username) 并打日志，逐步修数据直至无 null。

- **迁移策略**：  
  - 阶段 1：Flyway 增加 sys_user.auth_user_id（或创建 user_identity_map）；回填脚本按 username 关联；校验：无匹配的 sys_user 列表、auth_user_id 为 NULL 的活跃用户、重复 auth_user_id。  
  - 阶段 2：AuthUserVO 增加 authUserId；AuthService.getCurrentUser、AuthInterceptor 从 SysUser 取 auth_user_id 写入 VO；ProjectController/EvidenceController/EvidenceVersionController/ProjectService/EvidenceService 中需 UUID 处改为优先 user.getAuthUserId()，null 时 fallback resolveCreatedByUuid(username) 并打日志。  
  - 阶段 3：AdminUserService.create 在插入 sys_user 后插入 auth_user 并回填 sys_user.auth_user_id（同事务）；EvidenceService.resolveCreatedByUuid 保留用于 fallback 或 uploader=me 等查询，可内部优先用 request 中的 authUserId。  
  - 阶段 4：监控 auth_user_id 为 NULL 的登录用户数，趋零后考虑 NOT NULL 约束；长期可再演进到路径 A（业务表逐步迁到 sys_user）。

- **约束与规范**：username 在两表一致作为回填与 fallback 键；若允许修改 username，需同时更新 sys_user 与 auth_user 并保持 auth_user_id 不变。

- **代码改造点清单**：SysUser 实体与 SysUserMapper（select/insert/update）含 auth_user_id；AuthUserVO 增加 authUserId；AuthService.getCurrentUser 从 SysUser 取 auth_user_id 写入 VO；AuthInterceptor 无需改（仍用 getCurrentUser）；ProjectController.createProject/addMember/removeMember 等、EvidenceController、EvidenceVersionController 中 evidenceService.resolveCreatedByUuid(user.getUsername()) 改为 user.getAuthUserId() != null ? user.getAuthUserId() : evidenceService.resolveCreatedByUuid(user.getUsername()) 并打日志；ProjectService/EvidenceService 中入参可增加 UUID currentAuthUserId，由 Controller 传入；AdminUserService.create 在 insert auth_user 后 set user.setAuthUserId(authUser.getId()) 并 update sys_user（或 insert 后查出再 update）；EvidenceService.getVisibleProjectIds 可接受 username 或 authUserId，优先按 authUserId 查 auth_user 再查项目；Flyway：ADD COLUMN sys_user.auth_user_id；回填与校验脚本独立。

- **回滚**：阶段 1 回滚：删除 auth_user_id 列（若无可避免影响，仅停用读取）；阶段 2 回滚：代码回退为仅用 resolveCreatedByUuid，不读 auth_user_id。

- **工作量**：P0：Flyway + 回填 + 校验；AuthUserVO + getCurrentUser + 各 Controller 优先 authUserId；P1：AdminUserService 回填 auth_user_id、监控与文档；P2：NOT NULL 与后续演进路径 A 的评估。

---

## 五、分阶段实施计划（示意）与验证清单

### 5.1 分阶段实施（以推荐路径 C 为例）

| 阶段 | 内容 | 交付物 |
|------|------|--------|
| Phase 0 | 现状冻结与约定：username 修改策略、大小写规范、回填前检查清单 | 文档、回填前 SQL 清单 |
| Phase 1 | 新增 sys_user.auth_user_id；回填；校验（NULL/重复/无匹配）；AuthUserVO.authUserId、getCurrentUser 写入 | Flyway 脚本（仅 ADD COLUMN）、回填脚本、校验报表 |
| Phase 2 | Controller/Service 优先使用 user.getAuthUserId()，fallback + 日志；AdminUserService 创建时回填 auth_user_id | 代码改造清单完成、监控 auth_user_id 为 NULL 的请求 |
| Phase 3 | 可选 NOT NULL；文档化“单一真源”；后续路径 A 评估 | 约束与运维文档 |

### 5.2 验证与对账清单（合并前后可执行，至少 20 条）

1. **登录/登出/刷新**：使用 sys_user 存在的账号密码登录，Session 中有 LOGIN_USER_ID；登出后 Session 失效；再次请求 /api/auth/me 返回 401 或无当前用户。
2. **登录**：使用 auth_user 存在但 sys_user 不存在的 username（若存在此类数据）登录应失败。
3. **创建项目**：PMO 或普通用户登录后创建项目，请求体含 code/name/description，返回 200，project 表插入一条且 created_by 为对应用户的 auth_user.id(UUID)。
4. **创建项目-无法解析**：若对应用户在 auth_user 无记录且懒同步被关闭或失败，应返回 403「无法解析当前用户」。
5. **查询项目列表**：SYSTEM_ADMIN/PMO 见全部项目；普通用户仅见自己 created_by 或 auth_project_acl 中有其 auth_user.id 的项目。
6. **项目成员可见性**：项目详情与成员列表仅对“可见项目”的用户开放；无 ACL 且非 created_by 的用户访问项目详情返回 403。
7. **上传证据**：有 editor/owner 权限的用户上传，evidence_item.created_by 与 evidence_version.uploader_id 为该用户 UUID；viewer 上传返回 403。
8. **证据列表**：uploader=me 时仅返回当前用户为 created_by 的证据；列表在可见项目内按筛选条件过滤。
9. **证据详情**：可见项目内可查详情；不可见项目返回 403。
10. **证据状态机**：DRAFT→SUBMITTED（提交）、归档、作废；仅项目责任人（created_by/ACL owner）或 SYSTEM_ADMIN 可作废/归档；editor/viewer 调作废/归档接口返回 403。
11. **ACL 边界**：仅 owner 或 SYSTEM_ADMIN 可添加/移除成员、设 owner；移除后该用户不可见该项目（除非仍为 created_by）；至少保留一名 owner。
12. **成员选择器**：GET /api/users 返回 auth_user 列表（id, username, displayName）；新创建用户（双写后）应出现在列表中。
13. **禁用用户**：sys_user.enabled=false 后登录失败；该用户若仍在 auth_project_acl 中，成员列表仍展示直到被移除。
14. **删除用户**：若业务不允许物理删除，仅 is_deleted=true；删除后登录失败；其 created_by/ACL 历史记录保留，展示时需解析为“已删除用户”或保留原 display_name。
15. **并发**：同一用户首次创建项目时多请求并发，仅一条成功插入 auth_user（若懒同步），其余 DuplicateKey 后重查；所有请求最终得到同一 UUID 且项目创建成功一次。
16. **历史数据**：已有 project/evidence_item 的 created_by 为历史 auth_user.id；项目详情/证据详情/成员列表中展示的创建人、上传人、PM 应为对应用户 display_name（或 username），无“幽灵”或错乱。
17. **审计**：登录成功/失败/登出写入 audit_log，actor_user_id 为 sys_user.id（Long）；若存在 audit_operation_log 写入，actor_user_id 为 auth_user.id(UUID)。
18. **回填后**：所有 sys_user 中 is_deleted=false 且 enabled=true 的用户，auth_user_id 非 NULL（或已报表处理）；无重复 auth_user_id（若业务要求一一对应）。
19. **Fallback 行为**：若启用路径 C 的 fallback，auth_user_id 为 null 时用 username 解析，日志中有 fallback 记录；修数据后 fallback 不再触发。
20. **前端**：项目创建、成员管理、证据上传/作废/归档按钮的显隐与接口 403 一致；无“能点但 403”或“不能点但接口本应通过”的错位。

### 5.3 建议的最小自动化测试集合

- **单测**：  
  - AuthService.login：正确 username/password 返回 VO、Session 写入；错误密码/禁用用户 抛 UnauthorizedException。  
  - EvidenceService.resolveCreatedByUuid：username 在 auth_user 存在则返回其 id；不存在但在 sys_user 存在则插入 auth_user 并返回 id；都不存在返回 null；并发 DuplicateKey 后重查返回同一 id。  
  - PermissionUtil.checkCanInvalidate/checkCanArchive：created_by、ACL owner、SYSTEM_ADMIN 通过；editor/viewer 抛 403。  
  - ProjectService.createProject：传入 UUID 与 code/name/description，project 与 auth_project_acl 各插入一条，created_by 与 acl.user_id 为传入 UUID。

- **集成测**：  
  - 登录 → GET /api/auth/me → 创建项目 → GET /api/projects：链式校验 Session、当前用户、创建项目写库、列表可见。  
  - 登录（PMO）→ POST /api/projects（code/name/description）→ 201，DB 中 project.created_by = 对应用户 auth_user.id。  
  - 无 auth_user 仅有 sys_user 的用户登录 → 创建项目：若懒同步开启则成功；若关闭则 403。  
  - 用户 A（owner）添加用户 B 为 editor → 用户 B 登录 → 上传证据 → 成功；用户 B 作废证据 → 403。

---

## 六、决定性事实证据（5 条，指向具体代码/SQL 位置）

1. **Token/Session 中仅存 sys_user.id (Long)**  
   - AuthService.java L61：`session.setAttribute(SESSION_LOGIN_USER_ID, user.getId());`  
   - AuthInterceptor.java L44：`Long userId = session == null ? null : (Long) session.getAttribute(AuthService.SESSION_LOGIN_USER_ID);`  
   - 无 JWT；当前用户身份来自 SysUser。

2. **创建项目写入的用户标识为 auth_user.id (UUID)**  
   - ProjectController.java L97：`UUID currentUserId = evidenceService.resolveCreatedByUuid(user.getUsername());`，为 null 则 403。  
   - ProjectService.java L157-158：`project.setCreatedBy(userId);` → ProjectMapper.insert。  
   - ProjectMapper.xml / V1__init.sql：project.created_by UUID NOT NULL REFERENCES auth_user(id)。

3. **ACL 表存的是 auth_user.id (UUID)**  
   - V1__init.sql L49-50：auth_project_acl.user_id UUID NOT NULL REFERENCES auth_user(id)。  
   - ProjectService.java L161-164：acl.setUserId(userId)（即传入的 UUID）→ authProjectAclMapper.insert。  
   - PermissionUtil.java L45、L117 等：selectByProjectIdAndUserId(projectId, userId) 中 userId 为 UUID。

4. **当前用户 UUID 全链路通过 username 解析，AuthUserVO 无 UUID**  
   - AuthUserVO.java：仅 id(Long)、username、realName、roleCode、enabled。  
   - EvidenceService.java L352-374：resolveCreatedByUuid(String username) 内 authUserMapper.selectByUsername(username)，无则 sysUserMapper.selectByUsername 后 insert auth_user。  
   - ProjectController/EvidenceController/EvidenceVersionController 多处：evidenceService.resolveCreatedByUuid(user.getUsername())。

5. **审计双轨：audit_log 用 Long，audit_operation_log 用 UUID**  
   - V4__init_user_and_audit.sql L51：audit_log.actor_user_id BIGINT（无 FK）。  
   - AuthService.recordAudit 调用处传入 actorUserId 为 sys_user.id (Long)。  
   - V1__init.sql L110-111：audit_operation_log.actor_user_id UUID NOT NULL REFERENCES auth_user(id)；AuditOperationLogMapper.xml 中 actor_user_id 为 UUID typeHandler。

---

*文档结束。未执行任何代码或数据库修改。*
