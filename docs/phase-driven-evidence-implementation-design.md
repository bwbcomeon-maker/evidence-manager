# 阶段任务驱动证据改造 — 现状核对清单与实现设计说明

## 一、现状核对清单

### 1）biz_type 在后端/前端的所有引用点

| 位置 | 文件路径 | 说明 |
|------|----------|------|
| 实体字段 | `backend/app/src/main/java/com/bwbcomeon/evidence/entity/EvidenceItem.java` | 属性 `private String bizType` |
| DTO | `backend/app/src/main/java/com/bwbcomeon/evidence/dto/EvidenceListItemVO.java` | 属性 `private String bizType` |
| DTO | `backend/app/src/main/java/com/bwbcomeon/evidence/dto/EvidenceResponse.java` | 无 bizType（仅 status/evidenceStatus） |
| 枚举/校验 | `backend/app/src/main/java/com/bwbcomeon/evidence/service/EvidenceService.java` | `ALLOWED_BIZ_TYPES`（约 52–55 行）、104–113 行 type→bizType 规范化、135 行 setBizType、162 行日志 |
| 查询入参 | `backend/app/src/main/java/com/bwbcomeon/evidence/service/EvidenceService.java` | 245、251–258 行 listEvidences(bizType)、259 行 selectByProjectIdWithFilters(bizType) |
| Controller | `backend/app/src/main/java/com/bwbcomeon/evidence/web/EvidenceController.java` | 65、75、81、84 行 listEvidences 的 @RequestParam bizType 与日志 |
| Mapper 接口 | `backend/app/src/main/java/com/bwbcomeon/evidence/mapper/EvidenceItemMapper.java` | 58、66 行 selectByProjectIdWithFilters(bizType) |
| Mapper XML | `backend/app/src/main/resources/mapper/EvidenceItemMapper.xml` | 19 行 resultMap biz_type→bizType；30、75 行 Base_Column_List；90 行 insert COALESCE(#{bizType},'OTHER')；111 行 update；136–137 行 selectByProjectIdWithFilters AND biz_type |
| 迁移脚本 | `backend/app/src/main/resources/db/migration/V3__add_biz_type_to_evidence_item.sql` | 新增 biz_type 列与索引 |
| 前端 API 类型 | `frontend/src/api/evidence.ts` | 29 行 EvidenceListItem.bizType；61 行 EvidenceListParams.bizType |
| 前端展示 | `frontend/src/views/evidence/EvidenceDetail.vue` | 6 行 van-cell 业务类型；85–87 行 bizTypeLabel(evidence.bizType) |
| 前端筛选/上传 | `frontend/src/views/ProjectDetail.vue` | 47 行 filterBizType；140 行 uploadForm.type→bizTypeMap；247、353、410、420、530、575、669 行 bizType 筛选/上传/展示 |
| 文档 | `frontend/证据管理界面实现说明.md`、`backend/app/src/docs/api-evidence-list.md` | 描述 bizType |
| 测试数据 | `backend/app/init-evidence-test-data.sql` | 57 行 insert 含 biz_type |

---

### 2）status 与 evidence_status 在后端/前端的所有引用点

| 位置 | 文件路径 | 说明 |
|------|----------|------|
| 实体 | `backend/app/src/main/java/com/bwbcomeon/evidence/entity/EvidenceItem.java` | 59 行 status；64 行 evidenceStatus |
| DTO | `backend/app/src/main/java/com/bwbcomeon/evidence/dto/EvidenceListItemVO.java` | 45 行 status；50 行 evidenceStatus |
| DTO | `backend/app/src/main/java/com/bwbcomeon/evidence/dto/EvidenceResponse.java` | 44 行 status；49 行 evidenceStatus |
| 写入 | `backend/app/src/main/java/com/bwbcomeon/evidence/service/EvidenceService.java` | 133 行 setStatus("active")、134 行 setEvidenceStatus("DRAFT")；204、285–286、385–386、440–441 行 response/vo 双写 status + evidenceStatus；412–419 行 resolveEvidenceStatusFromOld(status) |
| 状态机读取 | `EvidenceService.java` | 504、516 行 archiveEvidence/submitEvidence 中 current = evidenceStatus != null ? evidenceStatus : resolveEvidenceStatusFromOld(status) |
| 列表/分页 | `EvidenceService.java` | 245、259 行 listEvidences(status)；344、350–354、366 行 pageEvidence(status)；selectByProjectIdWithFilters(status) |
| Mapper XML | `backend/app/src/main/resources/mapper/EvidenceItemMapper.xml` | 15–16 行 resultMap status/evidence_status；30、75 行 Base_Column_List；84–86 行 insert status/evidence_status；109 行 update status；132–134 行 selectByProjectIdWithFilters 用 AND status=#{status}；156–162 行 PageWhereClause 用 COALESCE(evidence_status,status,'')；218、231 行 updateEvidenceStatus/updateEvidenceInvalidate 的 WHERE 含 COALESCE(evidence_status,status,'') 或 evidence_status<>'INVALID' |
| Mapper 接口 | `backend/app/src/main/java/com/bwbcomeon/evidence/mapper/EvidenceItemMapper.java` | 26 行 selectByProjectIdAndStatus(status)；65、85、97 行 selectByProjectIdWithFilters/selectPageWithFilters/countPageWithFilters(status) |
| Controller | `backend/app/src/main/java/com/bwbcomeon/evidence/web/EvidenceController.java` | 74、81、84 行 listEvidences 的 status 参数 |
| Controller | `backend/app/src/main/java/com/bwbcomeon/evidence/web/EvidenceVersionController.java` | 48、58 行 listEvidence 的 status 参数；64 行注释 evidenceStatus |
| 前端 | `frontend/src/utils/evidenceStatus.ts` | 3、10–18 行 getEffectiveEvidenceStatus：evidenceStatus 优先，否则 status |
| 前端 | `frontend/src/views/ProjectDetail.vue` | 376、399–401、429–434、713、722、725、760、795 行 evidenceStatus/status 展示与上传结果 |
| 前端 | `frontend/src/views/EvidenceUpload.vue` | 120、124、132、136、149、174、197、201、232 行 evidenceStatus 与 DRAFT 判断 |
| 前端 | `frontend/src/views/evidence/EvidenceDetail.vue` | 71、126 行 getEffectiveEvidenceStatus/evidenceStatus |
| 前端 | `frontend/src/composables/useEvidenceList.ts` | 6 行 getEffectiveEvidenceStatus/mapStatusToText |
| 前端 | `frontend/src/api/evidence.ts` | 34 行 EvidenceListItem.evidenceStatus；32 行 status |
| 前端 | `frontend/src/views/EvidenceList.vue` | 77 行 mock status:'archived'；101–116 行 getStatusType/getStatusText(status) |
| DB 迁移 | `backend/app/src/main/resources/db/migration/V1__init.sql` | evidence_item.status CHECK(active/invalid/archived) |
| DB 迁移 | `backend/app/src/main/resources/db/migration/V5__evidence_status_lifecycle.sql` | 新增 evidence_status，历史数据按 status 回填 evidence_status |

---

### 3）证据上传/编辑/列表/详情/状态变更相关 API、Service、Mapper、SQL

| 能力 | API | Service 方法 | Mapper / SQL |
|------|-----|--------------|--------------|
| 上传证据 | POST `/api/projects/{projectId}/evidences`，EvidenceController.uploadEvidence | EvidenceService.uploadEvidence(projectId, name, type, remark, file, userId, roleCode) | EvidenceItemMapper.insert；EvidenceVersionMapper.insert |
| 按项目列表 | GET `/api/projects/{projectId}/evidences`，EvidenceController.listEvidences | EvidenceService.listEvidences(projectId, nameLike, status, bizType, contentType, userId, roleCode) | EvidenceItemMapper.selectByProjectIdWithFilters；EvidenceVersionMapper.selectLatestVersionsByEvidenceIds |
| 全局分页列表 | GET `/api/evidence`，EvidenceVersionController.listEvidence | EvidenceService.pageEvidence(..., status, ...) | EvidenceItemMapper.selectPageWithFilters、countPageWithFilters |
| 证据详情 | GET `/api/evidence/{id}`，EvidenceVersionController.getEvidence | EvidenceService.getEvidenceById(id, userId, roleCode) | EvidenceItemMapper.selectById；EvidenceVersionMapper.selectLatestVersionByEvidenceId |
| 提交 | POST `/api/evidence/{id}/submit`，EvidenceVersionController.submitEvidence | EvidenceService.submitEvidence(id, userId, roleCode) | EvidenceItemMapper.updateEvidenceStatus |
| 归档（单条证据） | POST `/api/evidence/{id}/archive`，EvidenceVersionController.archiveEvidence | EvidenceService.archiveEvidence(id, userId, roleCode) | EvidenceItemMapper.updateEvidenceStatus |
| 作废 | POST `/api/evidence/{id}/invalidate`，EvidenceVersionController.invalidateEvidence | EvidenceService.invalidateEvidence(id, userId, roleCode, invalidReason) | EvidenceItemMapper.updateEvidenceInvalidate |

说明：当前没有“编辑证据元数据”的 API（无 PATCH/PUT evidence）；上传即创建，无单独编辑接口。

---

### 4）project.status 的写入路径与项目归档入口

| 位置 | 说明 |
|------|------|
| 写入 | `ProjectService.java` 约 106、158 行：创建项目、导入项目时 `project.setStatus(STATUS_ACTIVE)`；`ProjectMapper.update(Project)` 可更新任意字段（含 status），但当前无 Controller 调用 update 将 status 置为 archived。 |
| 读取 | `ProjectMapper.xml` 的 select 均带 status；`ProjectVO`、`Project` 有 status；前端 ProjectList/ProjectDetail/EvidenceByProject 仅展示 project.status（进行中/已归档）。 |
| 结论 | **不存在“项目归档”入口**：无 POST/PATCH 将 project.status 置为 archived 的接口；仅有证据单条归档（evidence_status→ARCHIVED）。 |

---

## 二、实现设计说明

**本版修正（三处强制修正后）：**  
- **清库与迁移分离**：Flyway 仅做结构变更 + 静态数据（delivery_stage / stage_evidence_template）；清库由独立脚本 `db/scripts/dev-reset.sql` 承担，执行顺序为先 dev-reset 再 Flyway。  
- **S1 比测报告**：采用 project.has_procurement + 模板 required_when='HAS_PROCUREMENT'，仅在有采购时参与必填校验。  
- **S5 二选一**：stage_evidence_template 增加 rule_group、group_required_count；S5 两项同组、group_required_count=1，门禁按“组内至少 1 项满足”校验。

---

### 1）数据结构变更清单

- **biz_type 移除**
  - 删除 `evidence_item.biz_type` 列（新建 Flyway 迁移：ALTER TABLE evidence_item DROP COLUMN IF EXISTS biz_type；删除 idx_evidence_item_project_id_biz_type）。
  - 后端：删除 Entity/DTO 的 bizType 属性；删除 ALLOWED_BIZ_TYPES 及所有 setBizType/getBizType、listEvidences(bizType)、selectByProjectIdWithFilters(bizType) 等；Controller 去掉 bizType 参数；Mapper 接口与 XML 去掉 biz_type 的读写与 WHERE。
  - 前端：删除 BIZ_TYPE_LABELS、bizType 筛选与上传时的 type 自由输入；上传改为从“当前阶段模板项”选 evidence_type_code（见下）。
  - 不修改 V3 迁移内容（保留历史），在新迁移中仅做 DROP。

- **evidence_type_code 引入与落库**
  - 在 `evidence_item` 上新增唯一类型字段：`evidence_type_code VARCHAR(100) NOT NULL`，表示该证据对应模板中的哪一项（如 S1_START_PHOTO、S2_LOGISTICS_SIGNED 等）。
  - 上传与后续“编辑证据”接口必须传 stage_id + evidence_type_code；列表/详情返回 evidence_type_code 及阶段信息。
  - 统计与门禁：仅按 evidence_item.evidence_type_code + stage_id 与 stage_evidence_template 对齐，按类型计数与必填/数量校验。

- **status vs evidence_status 单一真源**
  - **选择：只保留 evidence_status，删除 status。**
  - 库表：新建迁移，删除 evidence_item.status 列；所有原 status 的 CHECK/索引若仅针对 status 则一并删除。
  - 后端：Entity/DTO 只保留 evidenceStatus；EvidenceService 中删除 setStatus、resolveEvidenceStatusFromOld、所有对 item.getStatus() 的引用；列表/分页/详情/状态机一律只读写 evidence_status；Mapper XML 的 resultMap、Base_Column_List、INSERT/UPDATE、WHERE 中全部去掉 status，仅保留 evidence_status。
  - 前端：EvidenceListItem 等只保留 evidenceStatus；evidenceStatus.ts 只读 evidenceStatus，不再 fallback status；所有展示与判断仅用 evidenceStatus。

- **stage_id 必填**
  - evidence_item 新增 `stage_id BIGINT NOT NULL REFERENCES delivery_stage(id)`（在新迁移中加，且因允许清库，可直接 NOT NULL，无需默认值）。
  - 上传接口必传 stage_id（及 evidence_type_code）；列表/详情返回 stage_id、stage 名称等；所有按项目查证据的 SQL 可带 stage_id 过滤。

- **project.has_procurement 与模板 required_when（必须修正点2）**
  - project 表新增 **has_procurement BOOLEAN NOT NULL DEFAULT false**。
  - stage_evidence_template 表新增 **required_when VARCHAR(50) NULL**（取值如 'HAS_PROCUREMENT'；NULL 表示无条件，按 is_required 校验）。S1_PRODUCT_COMPARE 配置 required_when='HAS_PROCUREMENT'，仅当 project.has_procurement=true 时参与必填校验。

- **stage_evidence_template 的 rule_group / group_required_count（必须修正点3）**
  - stage_evidence_template 新增 **rule_group VARCHAR(50) NULL**、**group_required_count INT NULL**。同一 rule_group 内至少需满足 group_required_count 项（每项有效证据数≥min_count）则该组通过；S5 验收二选一即两组项 rule_group 相同、group_required_count=1。

---

### 2）清库与迁移的职责分离（禁止在 Flyway 中清库）

- **Flyway 迁移脚本仅允许做两件事**  
  - **结构变更**：建表/删表、ALTER（新增/删除列、约束、索引等）。  
  - **初始化静态字典数据**：INSERT 固定数据到 delivery_stage、stage_evidence_template（不依赖业务数据的参考表）。  
  - **禁止**：在 Flyway 脚本中执行 TRUNCATE、DELETE、DROP TABLE 等清空业务数据的操作。

- **清库：独立脚本，由人手动或仅 DEV 执行**
  - 提供独立脚本：**`db/scripts/dev-reset.sql`**（或项目内约定的路径，如 `backend/app/scripts/dev-reset.sql`）。  
  - 脚本内容：按依赖顺序清空业务表（**不**修改 flyway_schema_history、**不**清 sys_user 若需保留账号）。建议顺序：  
    1. TRUNCATE evidence_version CASCADE；  
    2. TRUNCATE evidence_item CASCADE；  
    3. TRUNCATE audit_operation_log（及 audit_log 若含 project/evidence 引用）；  
    4. TRUNCATE auth_project_acl CASCADE；  
    5. TRUNCATE project CASCADE；  
    6. 若存在 project_stage，则 TRUNCATE project_stage CASCADE。  
  - 执行顺序（开发环境）：  
    1. **先执行 dev-reset.sql**（清空上述业务表）；  
    2. **再启动应用或执行 Flyway**，使 Flyway 仅做“结构变更 + 插入 delivery_stage / stage_evidence_template”。  
  - 说明：清库后 delivery_stage、stage_evidence_template 由 Flyway 迁移中的 INSERT 再次写入；project、evidence_item 等为空，新数据由应用创建。若表结构变更依赖“先无数据”（如删列、改 NOT NULL），则必须先跑 dev-reset 再跑迁移，否则迁移中的 ALTER 可能因现有数据不满足约束而失败，此时需在迁移中做可逆的默认值/占位处理，或保证 dev-reset 在迁移前执行。

---

### 3）S1_PRODUCT_COMPARE「如有采购则必填」的可判断机制（必须修正点2）

- **决策：采用 B）project 增加 has_procurement + 模板 required_when**
  - **project 表**：新增字段 **`has_procurement BOOLEAN NOT NULL DEFAULT false`**（或命名 `delivery_mode` 等，MVP 用布尔即可），表示该项目是否含采购，由创建/编辑项目时填写。
  - **stage_evidence_template 表**：新增 **`required_when VARCHAR(50) NULL`**。取值约定：  
    - `NULL` 或空：按 is_required 判断，无额外条件；  
    - `'HAS_PROCUREMENT'`：仅当 project.has_procurement = true 时，该模板项才参与“必填”校验；若 has_procurement = false，则该项视为可选，不参与阶段完成/归档门禁的必填判定。
  - **S1_PRODUCT_COMPARE**：模板中配置为 evidence_type_code=S1_PRODUCT_COMPARE，is_required=true，**required_when='HAS_PROCUREMENT'**。  
  - **校验逻辑**：阶段完成与归档门禁在判断“某阶段是否满足必填”时，对每条模板项：若 required_when='HAS_PROCUREMENT' 且当前 project.has_procurement=false，则跳过该项（不要求有证据）；否则按 is_required + min_count 正常校验。
  - **影响**：创建/编辑项目接口需支持写入 has_procurement；项目列表/详情返回该字段；前端创建项目时增加“是否含采购”选项；阶段统计与门禁 Service 读取 project.has_procurement 并按 required_when 过滤参与必填的模板项。

---

### 4）S5 验收阶段「二选一」门禁的结构与校验（必须修正点3）

- **问题**：仅 is_required + min_count 无法表达“验收报告 **或** 终验专家评审报告 二选一满足即可”的 OR 逻辑。

- **最小可落地结构：rule_group + group_required_count**
  - **stage_evidence_template** 增加两列：  
    - **`rule_group VARCHAR(50) NULL`**：同组内的多项共享同一 rule_group 值（如 S5_ACCEPTANCE_OR_REVIEW）；NULL 表示不参与分组，按单条 is_required/min_count 校验。  
    - **`group_required_count INT NULL`**：在该 rule_group 内，至少需满足“有效证据条数达标”的项数。例如 S5 验收二选一：两项的 rule_group 均为 S5_ACCEPTANCE_OR_REVIEW，group_required_count=1，表示该组内至少 1 项满足即通过。
  - **约定**：同一 rule_group 内所有行的 group_required_count 相同（由初始化数据保证）；校验时按组聚合，不按单行 is_required 逐项强制。

- **门禁校验算法（S5 归档门禁）**
  - 取验收阶段（S5）下所有模板项。  
  - 按 rule_group 分组：rule_group 为 NULL 的项，每条单独视为一组（组大小 1）；相同 rule_group 的项归为同一组。  
  - 对每一组：  
    - 若组内只有 1 条（即 rule_group 为 NULL 的普通项）：按原规则，该项 is_required 且有效证据数 ≥ min_count 则组通过，否则不通过。  
    - 若组内有多条（OR 组）：取该组的 group_required_count = K；统计组内每一项的有效证据数是否 ≥ 该项 min_count，满足的项数记为 M；若 M ≥ K，则该组通过，否则不通过。  
  - S5 门禁通过条件：所有组均通过；任一组不通过则归档接口返回 400，并返回结构化缺失（例如“验收阶段：验收报告或终验专家评审报告至少需 1 项满足，当前 0 项”）。

- **MVP 数据**：S5_ACCEPTANCE_REPORT 与 S5_FINAL_REVIEW_REPORT 两行均设 rule_group='S5_ACCEPTANCE_OR_REVIEW'，group_required_count=1，min_count=1；is_required 可设为 false 或保留 true，由“组内至少 1 项满足”统一表达二选一。

---

### 5）后端接口清单与契约

- **上传证据**  
  - 接口：POST `/api/projects/{projectId}/evidences`。  
  - 必填参数：name、file、**stage_id**、**evidence_type_code**（必须属于当前阶段模板中的项，后端校验）。  
  - 可选：remark。  
  - 行为：校验 project 存在、当前用户有上传权限、stage_id 属于该项目可用阶段、evidence_type_code 在该阶段的模板中存在；写入 evidence_item（stage_id、evidence_type_code、evidence_status=DRAFT）；写 evidence_version。  
  - 返回：证据详情（含 evidenceStatus、stageId、evidenceTypeCode 等），不再含 bizType/status。

- **阶段统计**  
  - 接口：GET `/api/projects/{projectId}/stages/summary`（或 `/api/projects/{projectId}/stage-progress`）。  
  - 返回：每个阶段的模板项列表；每项当前已上传且“有效”的证据数量；是否满足必填与 min_count；缺失项列表（缺什么、差几份）；该阶段是否可标记完成（布尔 + 原因）。  
  - 统计口径见下。

- **标记阶段完成**  
  - 接口：POST `/api/projects/{projectId}/stages/{stageCode}/complete`（或 PATCH 同路径 body 含 action=complete）。  
  - 行为：校验项目与权限；按 stage_evidence_template 校验该阶段必填项与 min_count 是否全部满足；不满足则 400 并返回结构化缺失项；满足则更新 project_stage 为已完成、写 completed_at。

- **项目归档**  
  - 接口：POST `/api/projects/{projectId}/archive`（或 PATCH `/api/projects/{projectId}` body status=archived）。  
  - 行为：校验项目存在、未归档、当前用户有归档权限；**门禁**：验收阶段（S5）强制证据（验收报告或终验专家评审报告）必须满足模板要求；不满足则 400 并返回缺失项（缺什么、差几份）；满足则更新 project.status=archived，并可选写审计。

- **证据状态机（统一后仅 evidence_status）**  
  - 保持现有：POST submit、POST archive（单条证据）、POST invalidate；内部只读写 evidence_status，不再读写 status。  
  - 若有“证据编辑”接口（PATCH evidence 元数据），也只允许改 title/note 等，不允许改 evidence_status；状态变更仅通过 submit/archive/invalidate。

- **列表/详情**  
  - GET `/api/projects/{projectId}/evidences`：可选参数增加 stageId、evidenceTypeCode；去掉 bizType；返回项含 evidenceStatus、stageId、evidenceTypeCode（不再含 status、bizType）。  
  - GET `/api/evidence`：同上，筛选与返回统一为 evidence_status、stage_id、evidence_type_code。  
  - GET `/api/evidence/{id}`：返回 evidenceStatus、stageId、evidenceTypeCode 等，不含 status、bizType。

---

### 6）统计口径（有效证据）

- **用于 min_count 与门禁的“有效证据”**：  
  - evidence_item.evidence_status = 'SUBMITTED' 且未作废（即非 INVALID）；  
  - 或 evidence_status = 'ARCHIVED'（已归档的单条证据也算有效）。  
- **不计入**：evidence_status = 'DRAFT' 或 'INVALID'。  
- 统计时按 (project_id, stage_id, evidence_type_code) 分组，只计上述有效状态的条数，与 stage_evidence_template.min_count 比较；必填项要求至少 1 条有效证据，min_count>1 时需达到对应张数/份数。

---

### 7）权限点（复用现有 ACL/角色）

- **标记阶段完成**：与“项目编辑/管理”一致，仅项目 owner 或 SYSTEM_ADMIN/PMO 可调（具体与现有 PermissionUtil 中可写权限一致）。  
- **项目归档**：同上，仅项目 owner 或 SYSTEM_ADMIN/PMO；归档前门禁校验不通过时仅返回 400，不改变权限逻辑。

---

### 8）任务拆分清单（按依赖顺序）

| 序号 | 任务 | 类型 | 依赖 |
|------|------|------|------|
| 1 | **独立清库脚本**：新增 `db/scripts/dev-reset.sql`，按依赖顺序 TRUNCATE evidence_version / evidence_item / audit_operation_log / auth_project_acl / project / project_stage；不碰 flyway_schema_history、sys_user。文档说明执行顺序：先执行 dev-reset.sql，再跑 Flyway 或启动应用 | **脚本** | 无 |
| 2 | **Flyway 迁移**：仅结构变更 + 静态数据。新增 delivery_stage、project_stage、stage_evidence_template 表；evidence_item 新增 stage_id、evidence_type_code，删除 status、biz_type 及相关索引；project 新增 has_procurement；stage_evidence_template 含 required_when、rule_group、group_required_count。INSERT 固定数据：delivery_stage 5 条、stage_evidence_template 全量（含 S1 required_when、S5 rule_group） | 数据迁移 | 无 |
| 3 | 后端 Entity/DTO：EvidenceItem 去掉 status、bizType，增加 stageId、evidenceTypeCode；EvidenceListItemVO/EvidenceResponse 同上；Project/ProjectVO 增加 hasProcurement；StageEvidenceTemplate 实体含 requiredWhen、ruleGroup、groupRequiredCount；删除 resolveEvidenceStatusFromOld、ALLOWED_BIZ_TYPES | 后端 | 2 |
| 4 | EvidenceItemMapper.xml/Java：去掉 status、biz_type 的读写与 WHERE；selectByProjectIdWithFilters 改为 evidence_status 与 evidence_type_code；insert/update 只写 evidence_status、stage_id、evidence_type_code | 后端 | 3 |
| 5 | EvidenceService：上传必填 stage_id、evidence_type_code，校验模板；列表/分页/详情只使用 evidence_status、stage_id、evidence_type_code；删除 status/bizType 逻辑 | 后端 | 4 |
| 6 | EvidenceController/EvidenceVersionController：去掉 status、bizType 参数；上传接口增加 stage_id、evidence_type_code 参数 | 后端 | 5 |
| 7 | 阶段统计 Service + 接口：GET 阶段进度与缺失项；按 project.has_procurement + template.required_when 过滤必填项；按 rule_group + group_required_count 做组内满足数校验 | 后端 | 5 |
| 8 | 阶段完成 Service + 接口：POST 标记完成；门禁校验含 required_when、rule_group/group_required_count | 后端 | 7 |
| 9 | 项目归档 Service + 接口：POST 归档；验收阶段门禁使用 rule_group/group_required_count 实现二选一 | 后端 | 7 |
| 10 | 项目创建/编辑：支持 has_procurement；创建时初始化 project_stage 五条记录 | 后端 | 2, 3 |
| 11 | 前端：删除 bizType/status 引用；上传改为选阶段+选该阶段模板项（evidence_type_code）；列表/详情/筛选只用 evidenceStatus、stageId、evidenceTypeCode | 前端 | 6 |
| 12 | 前端：项目创建/编辑增加“是否含采购”；阶段 Tab/分组展示、阶段统计展示、标记完成按钮、项目归档按钮 | 前端 | 7, 8, 9, 10, 11 |

说明：**执行顺序**：先跑 dev-reset.sql（需清库时），再执行 Flyway 迁移（仅结构+静态数据）；1 与 2 无依赖，但 2 假设业务表可被 ALTER（若表中已有数据且新约束不满足，需先 1 再 2）。

---

## 三、MVP 模板数据约定（固定 5 阶段 + evidence_type_code）

以下为 stage_evidence_template 的约定编码与规则（插入到 Flyway 迁移的固定数据中；**禁止在 Flyway 中清库**，清库用 dev-reset.sql）：

- **S1 项目启动阶段**  
  - S1_START_PHOTO（启动现场照片，必填，1，无 required_when）；S1_START_REPORT（启动汇报相关材料，必填，1）；S1_IMPL_PLAN（项目实施计划，必填，1）；**S1_PRODUCT_COMPARE（项目前期产品比测报告，is_required=true，required_when='HAS_PROCUREMENT'，min_count=1）**——仅当 project.has_procurement=true 时参与必填校验。

- **S2 采购与设备到货阶段**  
  - S2_LOGISTICS_SIGNED（物流单照片签字版，1）；S2_ARRIVAL_PHOTO（设备到货现场照片，≥3）；S2_PACKAGE_PHOTO（外包装及配件照片，每品至少1套，MVP 可 1）；S2_NAME_PLATE（设备铭牌/合格证照片，每品至少1套，MVP 可 1）；S2_ARRIVAL_ACCEPTANCE（到货验收单用户签字，1）；S2_ARRIVAL_LIST（总体到货清单，1）；S2_QUALITY_GUARANTEE（产品质保证明截图，1）。

- **S3 环境搭建与实施阶段**  
  - S3_INSTALL_PHOTO（设备上架或软件安装照片，≥3）；S3_SITE_PHOTO（现场施工照片，≥3）。

- **S4 联调测试阶段**  
  - S4_TEST_REPORT（测试报告，1）。

- **S5 验收阶段（归档门禁，二选一）**  
  - **S5_ACCEPTANCE_REPORT**（验收报告签字版）与 **S5_FINAL_REVIEW_REPORT**（终验专家评审报告签字版）：两行均设 **rule_group='S5_ACCEPTANCE_OR_REVIEW'，group_required_count=1**，min_count=1；组内至少 1 项有效证据数≥1 即通过，实现“二选一”门禁。

---

以上为现状核对与实现设计说明，无 DDL 与具体代码；确认后可进入“写迁移脚本 + 后端/前端改造”的实施阶段。
