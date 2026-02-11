# 前端驱动的接口契约与口径补充设计

本文档基于已定稿的【前端页面结构逻辑图与交互规则】，反向补齐后端总体设计中缺失的能力与口径，确保可 100% 实现该页面与交互。不写 DDL、不写具体代码，仅输出契约与口径，供实施阶段使用。

---

## 一、计算口径拍板（唯一定义，无备选）

### 1.1 有效证据

- **定义**：用于 min_count 统计、阶段完成度、归档门禁的“有效证据”，仅统计 **evidence_item.evidence_status IN ('SUBMITTED', 'ARCHIVED')** 的记录。
- **不计入**：evidence_status = 'DRAFT'（草稿）、'INVALID'（已作废）。
- **前端映射**：列表/详情中“已确认”= SUBMITTED 或 ARCHIVED；“草稿”= DRAFT。

### 1.2 参与计算的模板项（required_when 影响）

- **定义**：阶段完成度与归档门禁所涉及的“项”，仅包含 **当前项目下满足 required_when 条件的模板行**。
- **规则**：若模板行 required_when = 'HAS_PROCUREMENT'，则仅当 project.has_procurement = true 时，该项参与该项目的“总项数”与“必填/完成”判定；若 has_procurement = false，该项**不**计入分母、**不**参与必填、**不**参与阶段完成与归档门禁。
- **required_when 为 NULL 或空**：该项始终参与（按 is_required、min_count、rule_group 正常计算）。

### 1.3 阶段 x/y 的定义

- **y（分母）**：该阶段内**参与计算**的模板项数量。  
  - 若模板项无 rule_group：1 行 = 1 项，y 加 1（且需满足 required_when，下同）。  
  - 若多条模板行属于同一 rule_group：整组算 **1 项**，y 只加 1（例如 S5 两条验收报告/终验报告同组，y 只计 1）。
- **x（分子）**：该阶段内**已完成**的项数。  
  - 单项（无 rule_group）：该 evidence_type_code 下有效证据条数 ≥ 该行 min_count，则该项完成，x 加 1。  
  - 组（有 rule_group）：组内“有效证据数 ≥ 各自 min_count”的**行数** ≥ 该组 group_required_count，则整组算 1 项完成，x 加 1。
- **前端展示**：列表/总览中“到货阶段 3/6”即 x=3、y=6；进度条与健康度均基于 x、y 及是否含未满足必填项推导。

### 1.4 阶段完成度（百分比）与健康状态

- **阶段完成度**：`stageCompletionPercent = (y == 0) ? 100 : (x * 100 / y)`，四舍五入取整。
- **阶段健康状态**（供前端颜色与文案）：  
  - **COMPLETE**：x/y = 100%（即 x=y 且 y>0），或 y=0 视为完成。  
  - **PARTIAL**：0 < 完成度 < 100%，且存在未满足的**必填项**（该项 is_required 且当前有效证据数 < min_count，或组未满足 group_required_count）。  
  - **NOT_STARTED**：完成度 = 0%（x=0，y>0）。  
  - **MISSING**：0 < 完成度 < 100%，且无必填项缺失（仅选填未满）时，可与 PARTIAL 合并或单独；若需区分“严重缺失”，可用“存在必填项未满足”即 PARTIAL，“仅选填缺”为轻度。为简化，**统一**：有未满足必填 = PARTIAL，无必填缺且未满 100% = PARTIAL；x=0 = NOT_STARTED；100% = COMPLETE。

### 1.5 项目整体完整度 overallCompletionPercent

- **定义**：项目下所有**参与计算**的模板项（跨阶段、含 required_when 过滤、rule_group 按 1 项计）中，已完成项数 / 总项数 * 100，四舍五入取整。
- **公式**：  
  - 总项数 totalItems = 各阶段参与计算项数之和（每组 rule_group 计 1）。  
  - 已完成项数 completedItems = 各阶段已完成项数之和。  
  - overallCompletionPercent = totalItems == 0 ? 100 : (completedItems * 100 / totalItems)。

### 1.6 S5 二选一组在阶段完成与归档门禁中的计入方式

- **阶段完成度**：S5 阶段内两项（S5_ACCEPTANCE_REPORT、S5_FINAL_REVIEW_REPORT）同属 rule_group='S5_ACCEPTANCE_OR_REVIEW'，group_required_count=1。  
  - S5 的 y 只加 1（一组一项）。  
  - 组内至少 1 项有效证据数 ≥ min_count，则该组完成，S5 的 x 加 1。  
- **归档门禁**：验收阶段（S5）通过条件 = 该阶段所有参与计算的项均“完成”（含上述 S5 组满足 group_required_count）；与其它阶段一致，不单独区分“二选一”，统一按“S5 阶段完成”即可归档（再叠加全局必填见下）。

### 1.7 归档按钮门禁（与前端“申请归档”一致）

- **可归档条件（同时满足）**：  
  1. 所有**参与计算的**必填项（is_required=true 且满足 required_when）均已完成（单项有效证据数≥min_count，组满足 group_required_count）。  
  2. 各阶段完成度均为 100%（即每个阶段 x=y）**且**各阶段均已“标记完成”（project_stage.status=已完成）。  
  3. 项目当前 status ≠ archived（未归档）。
- **不可归档时**：返回 canArchive=false；GET stage-progress 返回 **archiveBlockReason**、**keyMissing**；POST archive 失败时 body 除 archiveBlockReason、keyMissing 外，**必须**含 **blockedByStages**（string[]）、**blockedByRequiredItems**（array of { stageCode?, evidenceTypeCode?, displayName, shortfall? }），便于前端提示与跳转定位。

---

## 二、单一聚合接口（支撑顶部完整度、阶段列表、健康度、缺失项、归档门禁）

### 2.1 接口契约

- **方法与路径**：`GET /api/projects/{projectId}/stage-progress`（或 `/api/projects/{projectId}/stages/summary`，二选一命名即可）。
- **权限**：与项目详情一致，当前用户须有该项目可见权限。
- **响应**：单一大对象，包含项目级完整度、关键缺失、阶段列表（含每阶段 x/y、健康状态、模板项级完成情况）、归档是否可点及不可点原因。

### 2.2 响应 JSON 结构约定

- **顶层字段**  
  - `overallCompletionPercent`：number，0–100。  
  - `keyMissing`：string[]，未满足的必填项展示名（或 evidence_type_code），用于顶部“关键缺失”和归档悬浮提示，最多返回若干条（如 10）。  
  - `canArchive`：boolean。  
  - `archiveBlockReason`：string | null，不可归档时文案（如“缺少验收报告，不可归档”）。  
  - `stages`：数组，见下。  
  - 可选：`projectStatus`、`projectName`、`hasProcurement`，便于前端顶部展示。

- **stages[] 元素**  
  - `stageCode`：string（如 S1/S2/…/S5）。  
  - `stageName`：string（如“项目启动阶段”）。  
  - `stageDescription`：string | null，阶段说明（来自 delivery_stage.description）。  
  - `itemCount`：number，即 y。  
  - `completedCount`：number，即 x。  
  - `completionPercent`：number，0–100。  
  - `healthStatus`：'COMPLETE' | 'PARTIAL' | 'NOT_STARTED'（与 1.4 一致）。  
  - **`stageCompleted`**：boolean，阶段是否已“标记完成”，以 **project_stage.status=已完成** 为准；用于前端展示✅完成。  
  - **`canComplete`**：boolean，x==y 时为 true，表示可点击“标记阶段完成”；x==y 但未点时前端展示“可完成但未完成”。  
  - `items`：该阶段内**模板行列表**（每行一条，不按 rule_group 折算），每项见下。

- **stages[].items[] 元素**（模板行列表；S5 二选一为两行，通过 group 字段合并展示）  
  - `evidenceTypeCode`：string。  
  - `displayName`：string。  
  - `isRequired`：boolean。  
  - `minCount`：number。  
  - `currentCount`：number，该类型有效证据条数。  
  - `completed`：boolean，行级是否满足（currentCount >= min_count）。  
  - `ruleGroup`：string | null，所属组。  
  - **`groupCompleted`**：boolean | null，属组时表示该组是否通过；不属组可为 null。  
  - **`groupDisplayName`**：string | null，属组时为整组展示名（如“验收报告（签字版）或终验专家评审报告（签字版）”），前端可将同组多行合并为一条展示。  
  - 可选：`sortOrder`，用于前端排序。

### 2.3 完整 JSON 示例

```json
{
  "overallCompletionPercent": 78,
  "keyMissing": ["验收报告（签字版）", "到货验收单（用户签字）"],
  "canArchive": false,
  "archiveBlockReason": "缺少验收报告，不可归档",
  "projectName": "XX系统建设项目",
  "projectStatus": "active",
  "hasProcurement": true,
  "stages": [
    {
      "stageCode": "S1",
      "stageName": "项目启动阶段",
      "stageDescription": "用于证明项目正式启动及计划就绪",
      "itemCount": 4,
      "completedCount": 4,
      "completionPercent": 100,
      "healthStatus": "COMPLETE",
      "items": [
        {
          "evidenceTypeCode": "S1_START_PHOTO",
          "displayName": "启动现场照片",
          "isRequired": true,
          "minCount": 1,
          "currentCount": 1,
          "completed": true,
          "ruleGroup": null
        },
        {
          "evidenceTypeCode": "S1_PRODUCT_COMPARE",
          "displayName": "项目前期产品比测报告",
          "isRequired": true,
          "minCount": 1,
          "currentCount": 1,
          "completed": true,
          "ruleGroup": null
        }
      ]
    },
    {
      "stageCode": "S2",
      "stageName": "采购与设备到货阶段",
      "stageDescription": "用于证明设备到货真实性及现场情况",
      "itemCount": 6,
      "completedCount": 3,
      "completionPercent": 50,
      "healthStatus": "PARTIAL",
      "items": [
        {
          "evidenceTypeCode": "S2_LOGISTICS_SIGNED",
          "displayName": "物流单照片（签字版）",
          "isRequired": true,
          "minCount": 1,
          "currentCount": 1,
          "completed": true,
          "ruleGroup": null
        },
        {
          "evidenceTypeCode": "S2_ARRIVAL_PHOTO",
          "displayName": "到货现场照片",
          "isRequired": true,
          "minCount": 3,
          "currentCount": 1,
          "completed": false,
          "ruleGroup": null
        },
        {
          "evidenceTypeCode": "S2_ARRIVAL_ACCEPTANCE",
          "displayName": "到货验收单（用户签字）",
          "isRequired": true,
          "minCount": 1,
          "currentCount": 0,
          "completed": false,
          "ruleGroup": null
        }
      ]
    },
    {
      "stageCode": "S5",
      "stageName": "验收阶段",
      "stageDescription": "用于证明项目通过验收",
      "itemCount": 1,
      "completedCount": 0,
      "completionPercent": 0,
      "healthStatus": "NOT_STARTED",
      "items": [
        {
          "evidenceTypeCode": "S5_ACCEPTANCE_REPORT",
          "displayName": "验收报告（签字版）",
          "isRequired": true,
          "minCount": 1,
          "currentCount": 0,
          "completed": false,
          "ruleGroup": "S5_ACCEPTANCE_OR_REVIEW"
        },
        {
          "evidenceTypeCode": "S5_FINAL_REVIEW_REPORT",
          "displayName": "终验专家评审报告（签字版）",
          "isRequired": true,
          "minCount": 1,
          "currentCount": 0,
          "completed": false,
          "ruleGroup": "S5_ACCEPTANCE_OR_REVIEW"
        }
      ]
    }
  ]
}
```

说明：S5 的 itemCount=1、completedCount=0 表示一组一项、当前未完成；items 仍可返回组内两行供前端展示“二选一”文案。

---

## 三、“按模板项查看证据实例列表”接口契约

### 3.1 用途

支撑前端“阶段展开 → 点击某一证据类型（如到货现场照片）”后的**已上传文件列表**：展示该类型下每条 evidence_item（及最新版本），以及状态（草稿/已确认）、上传人、时间、操作（查看/删除/确认）。

### 3.2 接口契约

- **方法与路径**：`GET /api/projects/{projectId}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences`。  
  - 或：`GET /api/projects/{projectId}/evidences?stageCode=xxx&evidenceTypeCode=xxx`（与现有按项目列表兼容时可用查询参数）。  
  - 推荐独立路径，语义清晰且与“阶段+模板项”层级一致。
- **路径参数**：projectId、stageCode（如 S2）、evidenceTypeCode（如 S2_ARRIVAL_PHOTO）。
- **权限**：与项目证据列表一致，当前用户须有该项目可见权限。
- **响应**：该 (project, stage, evidenceTypeCode) 下的 evidence_item 列表，每条含 id、title、note、evidenceStatus、evidenceTypeCode、stageId/stageCode、createdByUserId、createdByDisplayName、createdAt、latestVersion（versionId、originalFilename、filePath、fileSize、createdAt）等；**不**做分页（单类型下条数有限），若需可加 page/pageSize。

### 3.3 响应 JSON 示例

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "evidenceId": 101,
      "projectId": 1,
      "stageCode": "S2",
      "evidenceTypeCode": "S2_ARRIVAL_PHOTO",
      "title": "到货现场照片-1",
      "note": null,
      "evidenceStatus": "SUBMITTED",
      "createdByUserId": 10,
      "createdByDisplayName": "张三",
      "createdAt": "2026-02-10T10:00:00+08:00",
      "latestVersion": {
        "versionId": 201,
        "versionNo": 1,
        "originalFilename": "IMG_001.jpg",
        "filePath": "project_1/evidence_101/v1_IMG_001.jpg",
        "fileSize": 1024000,
        "createdAt": "2026-02-10T10:00:00+08:00"
      }
    },
    {
      "evidenceId": 102,
      "projectId": 1,
      "stageCode": "S2",
      "evidenceTypeCode": "S2_ARRIVAL_PHOTO",
      "title": "到货现场照片-2",
      "note": null,
      "evidenceStatus": "DRAFT",
      "createdByUserId": 10,
      "createdByDisplayName": "张三",
      "createdAt": "2026-02-10T11:00:00+08:00",
      "latestVersion": {
        "versionId": 202,
        "versionNo": 1,
        "originalFilename": "IMG_002.jpg",
        "filePath": "project_1/evidence_102/v1_IMG_002.jpg",
        "fileSize": 980000,
        "createdAt": "2026-02-10T11:00:00+08:00"
      }
    }
  ]
}
```

前端据此渲染“已上传文件”列表，并根据 evidenceStatus 显示“已确认”/“草稿”及操作“确认”/“删除”等。

---

## 四、项目列表页避免 N+1：在列表接口中带完整度与关键缺失摘要

### 4.1 决策

- **在项目列表接口中直接返回**：每个项目的 **证据完整度** 与 **关键缺失摘要**，避免“先查项目列表再对每个项目调阶段聚合”的 N+1。
- **接口**：现有 `GET /api/projects`（项目列表）扩展响应，在每条 project 上增加字段（或嵌套对象）。

### 4.2 扩展字段（每条项目）

- **evidenceCompletionPercent**：number | null，项目整体证据完整度（与 1.5 overallCompletionPercent 同口径）；已归档项目可为 100 或按实际统计；若项目无任何阶段/模板参与计算可返回 null 或 100。
- **keyMissingSummary**：string[]，未满足的必填项展示名，最多 3–5 条，用于列表卡片“关键证据缺失：验收报告”等；无缺失时为空数组。
- 可选：**stageHealthSummary**：各阶段 healthStatus 的简要（如 ['COMPLETE','PARTIAL','NOT_STARTED','NOT_STARTED','NOT_STARTED']），供列表页需展示阶段条时使用；若列表仅展示完整度+关键缺失则可暂不返回。

### 4.3 实现要点（供实施阶段）

- 列表接口在一次请求内：查可见项目列表 + 对每个项目做“阶段+模板项+有效证据”的聚合（或批量 SQL/子查询），算出 evidenceCompletionPercent 与 keyMissingSummary，避免 N+1 次阶段聚合调用。
- 若数据量较大，可考虑缓存或冗余表存储“项目最新完整度与关键缺失”，由定时或事件更新；MVP 可在列表查询时实时计算并控制可见项目数量与查询复杂度。

### 4.4 列表项 JSON 示例（扩展后）

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "code": "PROJ-001",
      "name": "项目A",
      "description": "...",
      "status": "active",
      "evidenceCompletionPercent": 78,
      "keyMissingSummary": ["验收报告"]
    },
    {
      "id": 2,
      "code": "PROJ-002",
      "name": "项目C",
      "status": "archived",
      "evidenceCompletionPercent": 100,
      "keyMissingSummary": []
    }
  ]
}
```

---

## 五、当前总体设计还缺的字段与接口

### 5.1 数据与字段

- **delivery_stage.description**：阶段说明文案（如“用于证明设备到货真实性及现场情况”），前端“阶段展开”顶部展示，必须在 delivery_stage 表或等效结构中存在；若当前设计仅有 name/code，需增加 description 字段。
- **stage_evidence_template.sort_order**：当前设计已含 sort_order，用于阶段内模板项展示顺序；无需新增，实施时保证写入与返回即可。
- **stage_evidence_template.priority**：当前未要求。前端有“关键缺失”强调（如顶部、列表页），**不强制**新增 priority；关键缺失可统一定义为“所有未满足的必填项（含 required_when 满足的）”，keyMissing/keyMissingSummary 已覆盖。若后续需区分“归档门禁项”与“一般必填”的展示优先级，可再增加 priority（如 CRITICAL/HIGH/NORMAL），MVP 可不加。

### 5.2 接口与能力

- **单一聚合接口** `GET /api/projects/{projectId}/stage-progress`：已在第二节约定，当前总体设计中的“阶段统计接口”需与此契约对齐（返回结构、字段名、口径一致）。
- **按模板项证据实例列表** `GET /api/projects/{projectId}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences`：第三节已约定，为新增接口。
- **项目列表扩展**：GET /api/projects 响应中增加 evidenceCompletionPercent、keyMissingSummary（及可选 stageHealthSummary），第四节已约定。
- **项目归档接口**：已有设计；失败时 400 body **必须**含：archiveBlockReason、keyMissing、**blockedByStages**（string[]）、**blockedByRequiredItems**（array of { stageCode?, evidenceTypeCode?, displayName, shortfall? }），便于前端提示与跳转定位。
- **阶段完成接口**：已有设计；失败时返回缺失项列表，与 stage-progress 的 items[].completed 及 keyMissing 口径一致即可。
- **上传证据**：已有设计；需保证请求体/参数含 stage_id、evidence_type_code；若前端需要“替换”能力，可为同一 evidence_type_code 下“新增一条”或“编辑已有证据”的语义，由产品决定；若为“替换”，可能需接口支持“指定某条证据上传新版本”或“作废旧证据+上传新证据”，当前设计已有作废与上传，可组合实现替换。  
  - **required_when 与上传**：**允许**对任意模板行上传证据（不因 required_when 不满足而拒绝上传）。当某行的 required_when 不满足时（如 HAS_PROCUREMENT 且 project.has_procurement=false），该行上的证据**不计入**阶段 x/y、overallCompletionPercent、归档门禁与 keyMissing；仅当 required_when 满足后，该行才参与计算，其上证据才计入统计。  
  - **切换 has_procurement**：项目从“无采购”改为“有采购”后，原 HAS_PROCUREMENT 行开始参与计算，若该行已有上传证据则立即计入 x/y 与门禁；从“有采购”改为“无采购”后，该行退出参与计算，其证据不再计入统计与门禁。

### 5.3 前端“AI 提示”与“数量不足”等

- 前端有“当前照片数量不足3张”“未识别到设备整体堆放照片”等提示；**数量不足**可由前端根据 stage-progress 的 items[].currentCount 与 minCount 自行计算；**未识别到某类内容**若依赖后端（如图像识别），属扩展能力，当前总体设计可不包含，后端仅提供数量与完成状态即可。

---

## 六、与现有设计文档的对应关系

- **计算口径**（本文 1.1–1.7）与《阶段任务驱动证据改造 — 现状核对清单与实现设计说明》中的“统计口径”“阶段完成”“归档门禁”一致，本文做**唯一化、前端可落地**的拍板。
- **单一聚合接口**（本文第二节）对应设计说明中的“阶段统计接口”，响应结构按前端页面 100% 落地，实施时以本文 JSON 为准。
- **按模板项证据列表**（本文第三节）为设计说明中未显式写出的**新增接口**，本节补全契约。
- **项目列表扩展**（本文第四节）为设计说明中未显式写出的**列表能力扩展**，本节补全并明确 N+1 规避方式。
- **第五节**所列缺失项（delivery_stage.description、新增接口、列表扩展、归档/阶段完成返回结构）需在实施阶段一并实现，以保证前端页面与交互 100% 可实现。

---

以上为《前端驱动的接口契约与口径补充设计》全文，不写 DDL、不写具体代码，仅输出契约与口径，供后续实施阶段使用。
