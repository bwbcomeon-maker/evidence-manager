# 归档审批流升级 — 技术设计与分阶段执行计划

> 本文档为「申请归档」从一键归档升级为「PMO 审批、附件级驳回、退回修改及待办消息提醒」的**技术设计与开发行动指南**，不包含具体代码实现。

---

## 一、现状扫描结论

| 层级 | 现状 |
|------|------|
| **项目状态** | `Project.status` 仅两值：`active` / `archived`（`Project.java`、`ProjectService.STATUS_*`）。 |
| **归档入口** | `POST /projects/{projectId}/archive`，门禁通过后直接改 `project.status=archived` 并批量草稿转归档（`ProjectService.archive`）。 |
| **门禁** | `StageProgressService.computeStageProgress` 计算 `canArchive`、`archiveBlockReason`、`keyMissing`、`blockedByStages`、`blockedByRequiredItems`；`StageProgressVO` / `ArchiveBlockVO` 已存在。 |
| **证据状态** | `EvidenceItem.evidenceStatus`：DRAFT/SUBMITTED/ARCHIVED/INVALID（`EvidenceStatus`）；无“审核维度”（通过/不通过/待审、退回意见）。 |
| **权限** | `PermissionUtil.checkCanArchive`：仅 owner 或 PMO/SYSTEM_ADMIN 可归档；无“提交申请”与“审批”分离。 |
| **前端** | `ProjectDetail.vue`：`onArchiveClick` → `handleArchive`（草稿确认）→ `doArchive()` 调 `archiveProject`；无待办/消息中心。 |
| **消息/待办** | 无通知表、无待办接口。 |

---

## 二、数据字典与模型调整

### 2.1 数据库表

| 操作 | 表名（建议） | 说明与字段要点 |
|------|--------------|----------------|
| **新增** | `project_archive_application` | 归档申请单。字段：`id`、`project_id`、`applicant_user_id`、`status`（见下枚举）、`submit_time`、`approver_user_id`、`approve_time`、`reject_time`、`reject_comment`（全文）、`created_at`、`updated_at`。约束：同一 `project_id` 同一时刻仅允许一条 `status = 'PENDING_APPROVAL'`；已退回为历史记录，不限制新申请创建。 |
| **新增** | `archive_reject_evidence` | 不符合项（附件级退回）。字段：`id`、`application_id`、`evidence_id`、`reject_comment`、`created_by`、`created_at`。 |
| **修改** | `project` | 增加 `status` 取值：在现有 `active`、`archived` 基础上增加 `pending_approval`（待审批/归档审核中）、`returned`（被退回）。或保持 `active`/`archived`，用申请单状态驱动“待审批/被退回”的展示与锁定逻辑（见 2.2 两种方案）。 |
| **新增** | `notification`（或 `user_todo`） | 待办/消息。字段：`id`、`user_id`、`type`（如 `ARCHIVE_PENDING`/`ARCHIVE_RETURNED`）、`title`、`body`、`related_project_id`、`related_application_id`、`link_path`（前端跳转路径）、`read_at`、`created_at`。 |

说明：若希望“项目锁定”完全由项目表表达，则 `project.status` 增加 `pending_approval`、`returned`；若希望项目表不改、仅用申请单状态，则前端/后端通过“是否存在 PENDING_APPROVAL/RETURNED 申请”判断锁定与展示。

### 2.2 后端实体与枚举

| 类型 | 名称 | 调整内容 |
|------|------|----------|
| **枚举** | `ProjectStatus`（新建）或沿用 String | 取值：`ACTIVE`、`PENDING_APPROVAL`、`RETURNED`、`ARCHIVED`。若 DB 仍存小写，在实体层做映射。 |
| **枚举** | `ArchiveApplicationStatus`（新建） | 取值：`PENDING_APPROVAL`、`APPROVED`、`REJECTED`。 |
| **实体** | `Project` | 若表增加状态：`status` 类型改为枚举或仍 String，取值扩展为上述 4 种。 |
| **实体** | `ProjectArchiveApplication`（新建） | 与表 `project_archive_application` 一一对应；含关联查询时可带 `List<ArchiveRejectEvidence>`。 |
| **实体** | `ArchiveRejectEvidence`（新建） | 与表 `archive_reject_evidence` 对应。 |
| **实体** | `Notification` / `UserTodo`（新建） | 与 `notification` 表对应。 |
| **实体** | `EvidenceItem` | **可选**：增加 `audit_status`（如 PENDING/PASS/REJECT）、`reject_comment`（审核维度）；或不在证据表加字段，仅用 `archive_reject_evidence` 表记录“某次申请下某证据被标不符合”。推荐：证据表不加字段，仅用关联表，便于多轮申请历史。 |

### 2.3 后端 VO/DTO

| 类型 | 名称 | 用途与字段要点 |
|------|------|----------------|
| **VO** | `ArchiveApplicationVO`（新建） | 列表/详情：`id`、`projectId`、`projectName`、`applicantUserId`、`applicantDisplayName`、`status`、`submitTime`、`approverUserId`、`approveTime`、`rejectTime`、`rejectComment`、`rejectEvidences`（List<RejectEvidenceItemVO>）。 |
| **VO** | `RejectEvidenceItemVO`（新建） | 不符合项：`evidenceId`、`evidenceTitle`、`stageName`、`evidenceTypeDisplayName`、`rejectComment`。 |
| **VO** | `TodoItemVO` / `NotificationVO`（新建） | 待办：`id`、`type`、`title`、`body`、`relatedProjectId`、`relatedApplicationId`、`linkPath`、`readAt`、`createdAt`。 |
| **DTO** | `ArchiveRejectRequest`（新建） | 退回：`comment`（必填）、`evidenceComments`（List<{ evidenceId, comment }>）可选。 |
| **扩展** | `ProjectVO` | 增加 `archiveApplicationStatus`（可选）、或 `archiveApplication` 精简字段（当前申请单状态），供前端判断“待审批/已退回/无申请”。 |
| **扩展** | `StageProgressVO` | 在“待审批”下可扩展：`canSubmitArchive`（PM 是否可提交申请）、`canApproveArchive`（当前用户是否可审批），或由权限接口单独返回。 |
| **扩展** | `EvidenceListItemVO` | 增加 `rejectComment`（当该证据属于当前项目某条 RETURNED 申请的不符合项时）、`rejectApplicationId`（可选），供证据列表/详情展示“不符合”及原因。 |
| **扩展** | `EvidenceResponse` / 详情 VO | 同上，证据详情接口返回“不符合”说明及关联的 `applicationId`。 |

---

## 三、接口契约调整计划

### 3.1 需修改的现有接口

| 接口 | 变更要点 |
|------|----------|
| `POST /projects/{projectId}/archive` | **行为变更**：仅在后端“审批通过”流程中调用（或改名为内部方法），不再对前端暴露为“一键归档”。或保留 URL，增加“若存在待审批申请则 4xx + 提示先走审批”的校验。 |
| `GET /projects/{id}` | 响应 `ProjectVO` 增加：当前项目归档申请状态（如 `archiveApplicationStatus: 'PENDING_APPROVAL' | 'RETURNED' | null`）或精简的 `archiveApplication` 对象，供前端区分“进行中 / 待审批 / 已退回 / 已归档”并控制按钮与只读。 |
| `GET /projects/{id}/stage-progress` | 响应 `StageProgressVO` 可扩展：在项目为 `pending_approval` 或存在 PENDING_APPROVAL 申请时返回 `canArchive=false` 且可带 `archiveBlockReason` 说明“归档审核中”；可选返回 `canSubmitArchive`、`canApproveArchive`（或由权限/角色在前端判断）。 |
| `GET /evidence`（项目下证据列表） | 若支持按 `projectId` 查询，响应中每条证据可带 `rejectComment`、`rejectApplicationId`（当存在 RETURNED 申请且该证据在不符项中时）。 |
| `GET /evidence/{id}` 或证据详情 | 响应增加：`rejectComment`、`rejectApplicationId`（同上），供证据详情页展示“PMO 标注的不符合原因”。 |

### 3.2 需新增的接口

| 方法/路径 | 请求 | 响应/说明 |
|-----------|------|-----------|
| `POST /projects/{projectId}/archive-apply` | 无 body 或 `{}` | 校验门禁（沿用现有 canArchive 逻辑）、草稿策略可选沿用；创建申请单 `status=PENDING_APPROVAL`；若项目表有 `pending_approval` 则更新项目状态；发待办给 PMO/管理员。返回 `ArchiveApplicationVO` 或 `applicationId`。 |
| `POST /projects/{projectId}/archive-approve` 或 `POST /archive-applications/{id}/approve` | 无 body 或 `{}` | 校验当前用户为 PMO/SYSTEM_ADMIN、申请单状态为 PENDING_APPROVAL；执行现有归档逻辑（`project.status=archived`、草稿转归档）；申请单状态改为 APPROVED；发待办/已读给申请人。返回统一成功结构。 |
| `POST /projects/{projectId}/archive-reject` 或 `POST /archive-applications/{id}/reject` | `ArchiveRejectRequest`：`comment`（必填）、`evidenceComments?: [{ evidenceId, comment }]` | 校验角色与申请单状态；写 `reject_comment`、写 `archive_reject_evidence`；申请单状态改为 REJECTED；项目若为 `pending_approval` 则改为 `returned`；发待办给项目 PM。返回统一成功结构。 |
| `GET /archive-applications` | Query：`status=PENDING_APPROVAL`、`role=approver`（待我审批）；或 `applicant=me`、`status=REJECTED`（我的被退回） | 分页列表，元素为 `ArchiveApplicationVO`。 |
| `GET /archive-applications/{id}` | - | 单条申请详情，含 `rejectEvidences`、项目摘要，供审批工作台/退回意见展示。 |
| `GET /me/todos` 或 `GET /notifications/todos` | Query：可选 `unreadOnly`、`type` | 当前用户待办列表：`TodoItemVO[]`，含 `type`（ARCHIVE_PENDING / ARCHIVE_RETURNED）、`linkPath`（如 `/projects/{id}?tab=evidence`）、`relatedProjectId`、`relatedApplicationId`。 |
| `PATCH /notifications/{id}/read` 或 `POST /notifications/read` | 可选 body `{ id }` 或 ids | 标记已读，更新 `read_at`。 |
| `GET /evidence/{id}/reject-info`（可选） | - | 若该证据在某条 RETURNED 申请的不符项中，返回 `{ rejectComment, applicationId }`；否则 404 或空。可与证据详情接口合并为详情中字段。 |

权限约定：`archive-apply` 仅项目 owner（或具备 canArchive 的成员）；`archive-approve`、`archive-reject` 仅 PMO、SYSTEM_ADMIN；待办接口按 `user_id` 过滤。

---

## 四、前端组件改造列表

### 4.1 需改造的现有 Vue 文件

| 文件 | 改造要点 |
|------|----------|
| `frontend/src/views/ProjectDetail.vue` | ① 根据 `project.status` 或 `archiveApplicationStatus` 分支：待审批 → PM 只读（隐藏上传/删除/申请归档）、顶部提示“归档审核中”；PMO 显示审批工作台（通过/退回）。② 已退回 → 显示退回原因、证据列表中标出“不符合”项及原因；“申请归档”改为“重新提交归档”并调 `archive-apply`。③ `onArchiveClick` / `handleArchive` / `doArchive`：PM 侧改为调 `archive-apply`，成功后提示“已提交审批”并刷新项目/申请状态；PMO 侧“通过”调 `archive-approve`（内部可再调现有 archive），“退回”调 `archive-reject` 并填表单。④ 保留草稿确认弹窗逻辑，在“提交申请”前仍可复用。⑤ 门禁失败仍用现有 `showArchiveBlockDialog` + `ArchiveBlockVO`。 |
| `frontend/src/views/evidence/EvidenceDetail.vue` | ① 若接口返回 `rejectComment` / `rejectApplicationId`，在详情页展示“不符合”标签及 PMO 填写的原因（只读）。② PMO 在“审批中”时：增加“标记为合格/不合格”操作；不合格时弹框填原因，写入当前申请单的不符项（调用后端“标记不符合”或退回时一并提交 evidenceComments）。 |
| `frontend/src/api/projects.ts` | ① 新增 `archiveApply`、`archiveApprove`、`archiveReject`、`getArchiveApplications`、`getArchiveApplicationById` 的接口声明与类型。② 新增 `TodoItemVO`、`getTodos`、`markTodoRead`（或 notifications API）。③ `ProjectVO` 类型增加 `archiveApplicationStatus` 或 `archiveApplication`。④ `StageProgressVO` 可选增加 `canSubmitArchive`、`canApproveArchive`。⑤ 保留 `archiveProject` 供审批通过流程使用或移除前端直接调用。 |
| `frontend/src/api/evidence.ts` | 证据列表/详情相关类型增加 `rejectComment`、`rejectApplicationId`（可选）；若单独接口 `getRejectInfo` 则在此声明。 |
| `frontend/src/views/ProjectList.vue` | 项目卡片/列表根据 `project.status` 或 `archiveApplicationStatus` 展示“进行中 / 待审批 / 已退回 / 已归档”标签或徽章；可选在卡片上展示“待审批”角标。 |
| `frontend/src/views/Me.vue` | 增加“待办事项”入口（或铃铛图标），跳转至待办中心页；可选显示未读待办数量角标（需 `GET /me/todos` 返回总数或未读数）。 |
| `frontend/src/router/index.ts` | 新增路由：`/me/todos`（或 `/todos`）→ 待办中心页；可选 `/archive/pending` → 待审批列表页（或与待办合并为 Tab）。 |
| `frontend/src/stores/auth.ts`（若有） | 若角标需要全局未读数，可增加请求 `todos` 未读数的逻辑（或在待办页内请求）。 |

### 4.2 需新增的组件/页面

| 类型 | 路径/名称 | 职责 |
|------|-----------|------|
| **页面** | `frontend/src/views/TodoCenter.vue`（或 `Me/TodoCenter.vue`） | 待办中心：Tab 或分组展示“待我审批”（PMO/管理员）、 “退回待办”（PM）；列表项展示项目名、时间、类型、操作人；点击跳转 `linkPath`（如 `/projects/{id}?tab=evidence`）。 |
| **页面** | `frontend/src/views/ArchivePendingList.vue`（可选） | 若待审批独立成页：仅 PMO/管理员可见，列表为 PENDING_APPROVAL 申请，操作“通过/退回”，点击进入项目证据 Tab。可与 TodoCenter 合并为一个页面两个 Tab。 |
| **组件** | `ArchiveApprovalBar.vue`（可选） | 审批操作区：固定在项目证据页底部或顶部，含“审批通过”“退回整改”按钮；退回时展开表单（整体意见 + 可选不符合项列表）。可内嵌在 `ProjectDetail.vue` 中而不单独文件。 |
| **组件** | `EvidenceRejectBadge.vue`（可选） | 在证据卡片/详情中展示“不符合”标签与 tooltip/气泡显示 `rejectComment`。可用内联模板替代。 |

---

## 五、分期执行路径建议

### 阶段一：数据与枚举（后端）

- 设计并落库：`project_archive_application`、`archive_reject_evidence`、`notification`（或 `user_todo`）；确定 `project.status` 是否扩展（`pending_approval`、`returned`）或仅用申请单状态。
- 新增枚举：`ArchiveApplicationStatus`；可选 `ProjectStatus`。
- 新增实体：`ProjectArchiveApplication`、`ArchiveRejectEvidence`、`Notification`；Mapper 与 XML。
- 不改现有 `archive` 业务逻辑，为下一阶段接口做准备。

**交付物**：表结构、迁移脚本、实体与 Mapper，无接口行为变更。

---

### 阶段二：核心状态流转接口（后端）

- 实现 `POST /projects/{projectId}/archive-apply`：门禁 + 草稿策略（可复用现有逻辑）、写申请单、可选改项目状态、发待办。
- 实现 `POST archive-approve`：权限校验、调现有 `ProjectService.archive`、更新申请单、发通知。
- 实现 `POST archive-reject`：写退回意见与 `archive_reject_evidence`、更新申请单与项目状态、发待办。
- 实现 `GET /archive-applications`（列表）、`GET /archive-applications/{id}`（详情）。
- 调整 `GET /projects/{id}` 返回申请状态（或 `archiveApplication`）；必要时调整 `GET /projects/{id}/stage-progress` 在“待审批”下的 `canArchive`/说明。

**交付物**：PM 可提交申请、PMO 可审批/退回，项目仅在审批通过后变为已归档；前端尚未改。

---

### 阶段三：前端项目详情与申请/审批流程

- `ProjectDetail.vue`：根据项目/申请状态切换“只读 / 审批工作台 / 已退回”；“申请归档”改为调用 `archive-apply`；“通过/退回”调用新接口；保留草稿确认与门禁失败弹窗。
- `projects.ts`：新接口与类型、`ProjectVO`/`StageProgressVO` 扩展。
- 前端不再直接调用 `archive`（或仅保留在“审批通过”的流程中由后端内部调用）。

**交付物**：前后端联调完成“提交申请 → 审批/退回 → 再次提交”主流程。

---

### 阶段四：附件级不符合项与证据展示

- 后端：`archive-reject` 支持 `evidenceComments`；证据列表/详情接口返回 `rejectComment`、`rejectApplicationId`（或实现 `GET /evidence/{id}/reject-info`）。
- `EvidenceDetail.vue`：展示“不符合”标签与原因；PMO 审批中可“标记不合格”并填原因（写回申请单或退回时汇总）。
- `ProjectDetail.vue`：证据网格/列表中，被标不符合的附件高亮（如红框/角标），点击可看原因。

**交付物**：退回可针对附件标注，PM 在列表/详情可见并修改后再次申请。

---

### 阶段五：待办与消息中心

- 后端：`GET /me/todos`（或 `/notifications/todos`）、`PATCH /notifications/{id}/read`；在申请提交/审批通过/退回时写入 `notification` 并关联 `linkPath`。
- 前端：新建 `TodoCenter.vue`，展示两类待办并跳转 `linkPath`；`Me.vue` 增加入口与可选角标；路由注册。

**交付物**：PM 与 PMO/管理员有统一待办入口，从提醒可直达项目证据页。

---

### 阶段六：列表与体验收尾

- `ProjectList.vue`：项目状态/申请状态展示（进行中/待审批/已退回/已归档）、可选角标。
- 权限与错误文案统一（403/400 提示）、移动端审批栏与退回表单体验优化。

**交付物**：全流程可闭环使用，状态清晰、入口统一。

---

## 六、小结

- **数据**：新增申请单、不符合项、通知三张表；项目状态可选扩展；证据层通过关联表支持“不符合”展示，无需改证据生命周期枚举。
- **接口**：现有归档接口改为“仅审批通过后使用”；新增申请/审批/退回与待办接口；项目与证据接口做最小扩展以支持状态与不符合展示。
- **前端**：以 `ProjectDetail.vue`、`EvidenceDetail.vue`、`projects.ts` 为核心改造；新增待办中心与可选审批栏组件；`ProjectList`、`Me`、路由配合扩展。
- **节奏**：先 DB 与枚举 → 再核心申请/审批/退回 API → 再前端项目详情与申请/审批 → 再附件不符合与证据展示 → 再待办中心 → 最后列表与体验收尾，便于分步上线与回滚。

---

---

## 七、阶段一交付说明（数据与枚举）

### 7.1 已交付

- **PostgreSQL 迁移**：`backend/app/src/main/resources/db/migration/V15__archive_approval_tables.sql`
  - `project_archive_application`：归档申请单，含唯一约束（同一项目同一时刻仅一条 PENDING_APPROVAL；已退回不限制新申请）。
  - `archive_reject_evidence`：附件级不符合项。
  - `notification`：消息待办。
- **枚举**：`ArchiveApplicationStatus`（PENDING_APPROVAL / APPROVED / REJECTED）。
- **实体**：`ProjectArchiveApplication`、`ArchiveRejectEvidence`、`Notification`（Lombok @Data，与现有 MyBatis 风格一致）。

### 7.2 Project 实体与表（可选，阶段二前决定）

当前阶段**未修改** `project` 表与 `Project` 实体。若采用「项目表表达锁定」方案（项目状态包含待审批/已退回），需在阶段二前：

1. **库表**：新增迁移，扩展 `project.status` 的 CHECK 约束，允许 `'pending_approval'`、`'returned'`（与现有 `active`、`archived` 并存）。
2. **Project 实体类**：在 `status` 字段注释中补充允许取值；若有统一常量，可增加类常量或使用 `ProjectStatus` 枚举（与设计文档 2.2 节一致）。**不需要**在实体中新增与 `ProjectArchiveApplication` 的一对一关联（申请单通过 Mapper 按 `projectId` 查询即可）。

---

*文档版本：v1 | 作为后续开发行动指南，不包含具体代码实现。*
