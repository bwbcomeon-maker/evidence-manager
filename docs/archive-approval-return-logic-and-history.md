# 归档、审批、退回业务逻辑与历史数据梳理

## 一、结论摘要

| 问题 | 结论 |
|------|------|
| **每次退回的修改意见和证据级不符合项都有记录吗？** | **有。** 每次提交归档会产生一条新的申请单；每次退回会在该申请单上写入整体意见，并在 `archive_reject_evidence` 表中记录每条证据的不符合原因。多轮「提交→退回→再提交→再退回」在库中都会保留。 |
| **能查看相应的历史信息吗？** | **当前只能看到「最近一次退回」的信息。** 项目详情和证据列表使用的都是「最新一条 REJECTED 申请单」的整体意见和不符合项。**没有**按项目查询「所有归档申请历史」的接口与页面，历次申请/退回记录无法在前端查看。 |

---

## 二、业务逻辑概览

### 2.1 项目状态与申请单状态

- **项目状态**（`project.status`）：`active`（进行中）、`pending_approval`（归档审核中）、`returned`（已退回）、`archived`（已归档）。
- **申请单状态**（`project_archive_application.status`）：`PENDING_APPROVAL`（待审批）、`APPROVED`（已通过）、`REJECTED`（已退回）。

同一项目**同一时刻**只能有一条「待审批」申请（唯一约束）；已退回的申请单**不会删除**，会一直保留，作为历史记录。

### 2.2 流程简述

1. **申请归档**（PM / 有权限用户）  
   - 条件：项目为 `active` 或 `returned`，且当前没有待审批申请，且满足归档门禁（证据完成度等）。  
   - 行为：插入一条新的 `project_archive_application`（status=`PENDING_APPROVAL`），项目状态改为 `pending_approval`，给 PMO/管理员发待办。

2. **审批通过**（PMO / 系统管理员）  
   - 行为：当前待审批申请单改为 `APPROVED`，项目执行归档（`project.status=archived`，证据草稿转归档等），给申请人发通知。

3. **退回**（PMO / 系统管理员）  
   - 入参：整体退回意见（必填）+ 可选「证据级不符合项」列表（evidenceId + comment）。  
   - 行为：当前待审批申请单改为 `REJECTED`，写入 `reject_time`、`reject_comment`；先按 application_id 删除该申请单下原有 `archive_reject_evidence`，再按本次请求插入新的不符合项；项目状态改为 `returned`；给申请人发待办。

4. **再次申请**  
   - 再次点击「申请归档/重新申请归档」时，会再**插入一条新的申请单**（新 id），项目从 `returned` 变为 `pending_approval`。  
   - 因此：**每次提交都是一条新申请单，每次退回只更新当次那条申请单**，历史申请单全部保留在库中。

### 2.3 前端/接口当前使用的「退回信息」来源

- **项目顶栏退回意见**：`GET /projects/{id}` 在项目 `status=returned` 时，用 **最新一条 REJECTED 申请单** 的 `reject_comment` 赋给 `ProjectVO.rejectComment`。
- **证据列表中的「不符合」原因**：项目为 returned 时，用 **同一条「最新 REJECTED 申请单」** 的 id 查 `archive_reject_evidence`，得到 evidenceId → rejectComment 映射，赋给每条证据的 `rejectComment`。

因此：**无论之前退回过多少次，界面上只会展示「最近一次」退回的整体意见和证据级不符合项**；更早的退回记录虽然在库里，但没有接口和页面可以查看。

---

## 三、数据库设计

### 3.1 project_archive_application（归档申请单）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| project_id | BIGINT | 项目 ID |
| applicant_user_id | BIGINT | 申请人 |
| status | VARCHAR(32) | PENDING_APPROVAL / APPROVED / REJECTED |
| submit_time | TIMESTAMPTZ | 提交时间 |
| approver_user_id | BIGINT | 审批人（通过时） |
| approve_time | TIMESTAMPTZ | 通过时间 |
| reject_time | TIMESTAMPTZ | 退回时间 |
| reject_comment | TEXT | 退回意见全文 |
| created_at, updated_at | TIMESTAMPTZ | 创建/更新时间 |

- 约束：同一 `project_id` 下仅允许一条 `status = 'PENDING_APPROVAL'`（唯一索引 `idx_project_archive_application_project_pending`）。  
- 已退回（REJECTED）的申请不删除，可有多条，用于历史记录。

### 3.2 archive_reject_evidence（不符合项 / 附件级退回）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| application_id | BIGINT | 所属申请单 id |
| evidence_id | BIGINT | 证据 id（evidence_item.id） |
| reject_comment | TEXT | 不符合原因 |
| created_by | BIGINT | 标注人 |
| created_at | TIMESTAMPTZ | 创建时间 |

- 每条「退回」操作会先按 `application_id` 删除该申请单下旧的不符合项，再插入本次的 evidenceComments，因此**每个申请单**对应一份不符合项快照。  
- 多轮申请会产生多份申请单，每份申请单有各自的 `archive_reject_evidence` 行，因此**历次退回的证据级意见都在库里有记录**。

### 3.3 notification（待办/消息）

- 与归档相关类型：`ARCHIVE_PENDING`、`ARCHIVE_RETURNED`、`ARCHIVE_APPROVED` 等。  
- 含 `related_project_id`、`related_application_id`、`link_path`，用于待办列表跳转到项目/证据页。

---

## 四、代码实现要点

### 4.1 后端

| 模块 | 文件 | 要点 |
|------|------|------|
| 申请 | `ProjectArchiveServiceImpl.apply` | 校验项目状态（active/returned）、无待审批申请、门禁通过；`applicationMapper.insert(app)` 新增申请单；`projectMapper.updateStatusById(projectId, STATUS_PENDING_APPROVAL)`；发待办。 |
| 通过 | `ProjectArchiveServiceImpl.approve` | `selectPendingByProjectId` 取待审批单；`updateStatusAndAudit(APPROVED, ...)`；`projectService.archive(...)` 执行归档；发通知。 |
| 退回 | `ProjectArchiveServiceImpl.reject` | `rejectEvidenceMapper.deleteByApplicationId` 再按 request 的 evidenceComments 循环 `rejectEvidenceMapper.insert`；`updateStatusAndAudit(APP_REJECTED, ..., rejectTime, rejectComment)`；`projectMapper.updateStatusById(projectId, STATUS_RETURNED)`；发待办。 |
| 项目详情中的退回意见 | `ProjectService.getProjectDetail` | 当 `project.status == returned` 时，`projectArchiveApplicationMapper.selectLatestRejectedByProjectId(projectId)`，将 `rejected.getRejectComment()` 赋给 `ProjectVO.rejectComment`。 |
| 证据列表中的不符合原因 | `EvidenceService.listEvidencesByProject` | 调用 `getRejectCommentMapForProject(projectId)`：若项目为 returned，则 `selectLatestRejectedByProjectId` 取最新 REJECTED 申请，再 `archiveRejectEvidenceMapper.selectByApplicationId(rejected.getId())` 得到 evidenceId→rejectComment，赋给每条证据的 `rejectComment`。 |

### 4.2 Mapper 方法（与历史/展示相关）

- **ProjectArchiveApplicationMapper**
  - `selectPendingByProjectId(projectId)`：当前待审批申请（一条）。
  - `selectLatestRejectedByProjectId(projectId)`：该项目**最新一条** REJECTED 申请（按 reject_time/updated_at 倒序 LIMIT 1），用于项目详情和证据列表的「当前退回意见」展示。
  - **没有**：按 projectId 分页/列表查询所有申请单（即没有「申请历史」接口的底层支持）。

- **ArchiveRejectEvidenceMapper**
  - `selectByApplicationId(applicationId)`：某条申请单下的所有不符合项。
  - `deleteByApplicationId(applicationId)`：退回时先删再插，实现当次退回的不符合项覆盖。

### 4.3 接口

- `POST /api/projects/{projectId}/archive-apply`：申请归档。  
- `POST /api/projects/{projectId}/archive-approve`：审批通过。  
- `POST /api/projects/{projectId}/archive-reject`：退回，Body 为 `ArchiveRejectRequest`（comment 必填，evidenceComments 可选）。  

**当前没有**：
- `GET /api/projects/{projectId}/archive-applications`（按项目查申请历史）
- `GET /api/archive-applications`（按条件分页，如「待我审批」「我的被退回」）
- `GET /api/archive-applications/{id}`（单条申请详情，含 rejectEvidences）

因此**历史数据都在表里，但没有任何接口暴露「历次申请/退回」列表与详情**。

---

## 五、历史数据与可查看性小结

- **有记录**：每次提交一条新申请单；每次退回在该申请单上更新 reject_time、reject_comment，并在 archive_reject_evidence 中保存该次的不符合项；多轮提交/退回会产生多条申请单和多批不符合项，**全部保留**。  
- **可查看范围**：目前**仅能查看「最近一次退回」**对应的整体意见和证据级不符合项（项目详情顶栏 + 证据列表/详情中的 rejectComment）。  
- **不可查看**：历次申请单列表、历次退回意见、历次不符合项列表；没有「归档申请历史」相关的 API 与前端页面。

若需要「查看历次归档申请与退回记录」，需要在后端增加按 projectId（或按用户/状态）查询申请单列表及单条详情的接口，并在前端增加「归档申请历史」或「退回历史」的展示与入口。
