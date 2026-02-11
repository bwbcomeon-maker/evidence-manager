# 后端阶段任务驱动 — 实施计划

路线 A：先完成后端“阶段统计 + 门禁 + 阶段完成 + 项目归档”闭环，再进入前端改造。本文档为**后端实施计划**，不写 DDL/具体代码，仅描述能力、约束、口径、接口契约、算法与验收；经确认后再进入实际代码实现与自测。

---

## 一、目标与约束（必须达成 / 必须遵守）

### 1.1 必须达成的能力

- 按项目返回：整体证据完整度%、关键缺失项、风险提示（规则级）、阶段进度条数据、阶段折叠清单数据、每阶段模板项完成情况（含 minCount/currentCount/completed）。
- 按“阶段 + 证据类型（模板项）”返回该类型下的证据实例列表（含草稿/已确认/已作废），供证据项内部详情页。
- “标记阶段完成”：门禁不通过则返回结构化缺失项；通过则更新阶段完成状态。
- “项目归档”：门禁不通过则返回缺失项与提示文案；通过才允许将 project.status 置为 archived。

### 1.2 唯一口径（与《前端驱动的接口契约与口径补充设计》严格一致）

- **有效证据**：仅 evidence_status IN ('SUBMITTED','ARCHIVED') 计入数量、阶段完成度、归档门禁；DRAFT/INVALID 不计入。
- **required_when**：当 required_when='HAS_PROCUREMENT' 且 project.has_procurement=false 时，该模板项**完全**从分母剔除（不参与必填、不参与阶段完成、不参与归档门禁）。
- **S5 二选一**：同 rule_group 内满足 group_required_count 即组通过；组在 x/y 中按 1 项计。
- **阶段 x/y**：y = 参与计算的项数（单项=1，组=1）；x = 已完成项数（单项看有效证据数≥min_count，组看组规则）。
- **overall%**：全项目参与计算项中，已完成项数/总项数×100，跨阶段聚合，含 required_when 过滤与组折算。

### 1.3 约束

- 后端为唯一事实源：统计、门禁、缺失项均由后端计算并输出。
- 开发阶段可清空业务数据、调整字段、重构旧逻辑；须保证闭环与代码一致性。
- Flyway 仅做结构变更与静态字典数据初始化；禁止在 Flyway 中 TRUNCATE/DELETE。清库使用独立脚本（如 db/scripts/dev-reset.sql），执行顺序：先清库，再 Flyway（或启动应用触发 Flyway）。

---

## 二、需要新增/调整的核心数据模型点（思路级，不展开 DDL）

### 2.1 新增模型

- **delivery_stage**：阶段定义表。字段思路：id、code（如 S1–S5）、name、description（阶段说明，供前端展示）、sort_order。由 Flyway 插入 5 条固定数据。
- **project_stage**：项目阶段进度表。字段思路：project_id、stage_id（或 stage_code）、status（未开始/进行中/已完成）、completed_at。项目创建或首次需要时为该 project 初始化 5 条记录。
- **stage_evidence_template**：阶段证据模板表。字段思路：stage_id、evidence_type_code、display_name、is_required、min_count、sort_order、required_when（如 HAS_PROCUREMENT）、rule_group、group_required_count。由 Flyway 插入 MVP 固定模板数据（含 S1 required_when、S5 rule_group）。

### 2.2 证据表调整

- **evidence_item**：新增 stage_id（NOT NULL，关联 delivery_stage）、evidence_type_code（NOT NULL）；删除 status、biz_type。上传与列表/详情均依赖 stage_id + evidence_type_code；统计与门禁按 evidence_type_code + stage 与模板对齐。

### 2.3 项目表调整

- **project**：新增 has_procurement（BOOLEAN NOT NULL DEFAULT false）。创建/编辑项目时写入；阶段统计与门禁读取以应用 required_when 规则。

### 2.4 与现状的冲突与解决

- **冲突 1**：现有 evidence_item 存在 status 与 evidence_status 双轨、以及 biz_type。  
  **解决**：按既有设计删除 status、biz_type，仅保留 evidence_status；所有读写与状态机统一用 evidence_status。
- **冲突 2**：现有证据上传接口为 name + type + file，无 stage。  
  **解决**：上传接口增加必填参数 stage_id（或 stageCode）、evidence_type_code；校验 evidence_type_code 属于该阶段的模板项；写入 evidence_item 时写入 stage_id、evidence_type_code。
- **冲突 3**：项目列表当前仅返回项目基础字段，无完整度与缺失摘要。  
  **解决**：在列表查询链路中增加“按项目聚合阶段+模板+有效证据”的统计，为每条项目返回 evidenceCompletionPercent、keyMissingSummary（见第五节），避免 N+1。

---

## 三、计划新增/调整的接口清单（能力覆盖，字段名可微调）

### 3.1 A) 项目证据聚合总览接口

- **方法路径**：`GET /api/projects/{projectId}/stage-progress`（或 `/stages/summary`）。
- **输入**：projectId（路径参数）；当前用户从请求上下文获取，做项目可见性校验。
- **输出**（必须覆盖）：  
  - 项目级：overallCompletionPercent、keyMissing（必填缺失展示名列表，如 Top 10）、canArchive、archiveBlockReason；可选 projectName、projectStatus、hasProcurement。  
  - 阶段级：stages[]，每元素含 stageCode、stageName、stageDescription、itemCount（y）、completedCount（x）、completionPercent、healthStatus（COMPLETE/PARTIAL/NOT_STARTED）、**stageCompleted**（boolean，以 project_stage.status=已完成 为准）、**canComplete**（boolean，x==y）、items[]。  
  - **items[]**：**模板行列表**（每行 stage_evidence_template 一条，不按 rule_group 折算）。每项含：evidenceTypeCode、displayName、isRequired、minCount、currentCount、completed（行级）、ruleGroup；若该行属某 rule_group，则还含 **groupCompleted**（该组是否通过）、**groupDisplayName**（组展示名，供前端将同组多行合并展示，如 S5 二选一）。可选 sortOrder。
- **风险提示**：可为规则级文案（如“尚未完成验收阶段关键证据”），由后端根据 keyMissing 或阶段健康状态生成固定提示列表，不要求复杂规则引擎。

### 3.2 B) 按“阶段+模板项”查看证据实例列表接口

- **方法路径**：`GET /api/projects/{projectId}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences`。
- **输入**：projectId、stageCode、evidenceTypeCode（路径参数）；权限同项目证据列表。
- **输出**：该 (project, stage, evidenceTypeCode) 下的证据实例列表。每条含：evidenceId、projectId、stageCode、evidenceTypeCode、title、note、evidenceStatus、createdByUserId、createdByDisplayName、createdAt、latestVersion（versionId、versionNo、originalFilename、filePath、fileSize、createdAt）。不区分草稿/已确认/已作废时均可返回，由前端按 evidenceStatus 展示与操作（查看/删除/确认）。

### 3.3 C) 阶段完成接口

- **方法路径**：`POST /api/projects/{projectId}/stages/{stageCode}/complete`（或 PATCH，语义为“标记该阶段完成”）。
- **输入**：projectId、stageCode（路径参数）。
- **行为**：校验项目可见性及“标记阶段完成”权限（与项目写权限一致）；按唯一口径计算该阶段是否满足完成条件（参与计算的项全部完成：单项有效证据数≥min_count，组满足 group_required_count）；不满足则返回 4xx 及结构化缺失项（displayName 或 evidenceTypeCode + 差几份）；满足则更新 project_stage 为已完成并写入 completed_at。
- **输出**：成功时返回 200 及可选最新 stage 状态；失败时返回 400 及 body 中含 missingItems（列表）、message（提示文案）。

### 3.4 D) 项目归档接口

- **方法路径**：`POST /api/projects/{projectId}/archive`。
- **输入**：projectId（路径参数）。
- **行为**：校验项目存在、未归档、当前用户有归档权限；门禁校验：所有参与计算的必填项均已完成且各阶段完成度 100%（含 S5 二选一组通过）；不满足则返回 4xx 及结构化 body；满足则更新 project.status=archived，并可选写审计。
- **输出**：  
  - 成功 200。  
  - 失败 400，body **必须**含：**archiveBlockReason**（string）、**keyMissing**（string[]）、**blockedByStages**（string[]，未达 100% 或未标记完成的阶段 code）、**blockedByRequiredItems**（array of { stageCode?, evidenceTypeCode?, displayName, shortfall? }，未满足的必填项，shortfall 为差几份）。便于前端做提示与跳转定位。

### 3.5 项目列表扩展（避免 N+1）

- **接口**：现有 `GET /api/projects` 扩展响应体。
- **扩展字段**（每条项目）：evidenceCompletionPercent（number，与 overallCompletionPercent 同口径）、keyMissingSummary（string[]，最多 3–5 条，未满足必填项展示名）。实现方式：在列表查询时对当前页的 projectId 列表做一次批量聚合（或子查询/JOIN 聚合），算出每个项目的完整度与关键缺失，避免对每个项目再调一次 stage-progress。

### 3.6 已有接口的调整（与阶段闭环一致）

- **上传证据**：POST `/api/projects/{projectId}/evidences` 增加必填参数 stage_id（或 stageCode）、evidence_type_code；校验模板存在且属于该阶段；写入 evidence_item.stage_id、evidence_type_code；返回体含 evidenceStatus、stageId、evidenceTypeCode（不再含 status、bizType）。
- **按项目证据列表**：GET `/api/projects/{projectId}/evidences` 支持可选参数 stageId/stageCode、evidenceTypeCode；返回项含 evidenceStatus、stageId、evidenceTypeCode（删除 status、bizType）。
- **项目创建/编辑**：创建项目时支持 has_procurement；创建后为该 project 初始化 project_stage 五条记录（或首次访问阶段数据时懒初始化）。若有项目编辑接口，需支持更新 has_procurement。

---

## 四、门禁与统计的核心算法说明

### 4.1 参与计算的模板项过滤（required_when）

- 对当前 project，加载其所有阶段下的 stage_evidence_template 行。
- 对每一行：若 required_when = 'HAS_PROCUREMENT' 且 project.has_procurement = false，则**排除**该行（不进入后续 y、必填、阶段完成、归档门禁的任何计算）。  
- required_when 为 NULL 或空：该行始终参与。

### 4.2 有效证据计数

- 仅统计 evidence_item 中 evidence_status IN ('SUBMITTED','ARCHIVED') 且 project_id、stage_id、evidence_type_code 与当前项目/阶段/类型匹配的条数。  
- 按 (project_id, stage_id, evidence_type_code) 分组计数，得到每个模板项类型的 currentCount。

### 4.3 阶段内 x/y 与单项/组完成判定

- **y**：该阶段内参与计算的“项”数。无 rule_group 的行每行计 1 项；同一 rule_group 的多行整体计 1 项（去重 rule_group 后计数）。
- **单项完成**：该 evidence_type_code 的 currentCount ≥ 该行 min_count 则该项完成。
- **组完成**：同一 rule_group 内，统计“currentCount ≥ 该行 min_count”的行数 M；若 M ≥ 该组 group_required_count，则整组算 1 项完成。
- **x**：该阶段内已完成项数（单项完成则 x 加 1；组完成则该组贡献 1）。

### 4.4 阶段完成度与 healthStatus

- stageCompletionPercent = (y==0) ? 100 : round(x*100/y)。  
- healthStatus：x==y 且 y>0 为 COMPLETE；x==0 且 y>0 为 NOT_STARTED；否则 PARTIAL（有未满足必填或未满 100% 均视为 PARTIAL）。

### 4.5 整体完整度 overallCompletionPercent

- 全项目范围内，总项数 = 各阶段参与计算项数之和（rule_group 按 1 项计）；已完成项数 = 各阶段 x 之和。  
- overallCompletionPercent = totalItems==0 ? 100 : round(completedItems*100/totalItems)。

### 4.6 关键缺失 keyMissing / keyMissingSummary

- 遍历所有参与计算的模板项（含组内行）；对必填项（is_required 且满足 required_when）：若单项未完成或组未完成，则将该缺项加入 keyMissing（展示名或 evidence_type_code，去重）。  
- 组未完成时，可输出组代表展示名（如“验收报告（签字版）或终验专家评审报告（签字版）”）或组内第一个未满足的 displayName，由实现统一约定。

### 4.7 阶段完成门禁（标记阶段完成）

- 对该阶段所有参与计算的项：单项均需 currentCount≥min_count，组需满足 group_required_count。  
- 任一项/组不满足则不允许标记完成，返回 400 及该阶段缺失项列表。

### 4.8 项目归档门禁

- 条件 1：所有参与计算的必填项均已完成（单项与组规则同上）。  
- 条件 2：各阶段完成度均为 100%（即每阶段 x=y）。  
- 条件 3：project.status ≠ 'archived'。  
- 三者同时满足才允许归档；否则 400，返回 keyMissing、archiveBlockReason。

---

## 五、项目列表页避免 N+1 的实现思路

- **方案**：在 `GET /api/projects` 的 Service 层，取得当前页项目列表后，对这批 projectId 做**一次批量**阶段+模板+有效证据的聚合（同一套统计逻辑复用，按 projectId 列表入参），得到每个 projectId 的 evidenceCompletionPercent、keyMissingSummary（取前 3–5 条）。  
- **数据层**：可提供“按 projectIds 批量统计”的 Mapper 方法或 Service 方法，内部用 SQL 聚合（按 project_id 分组、按阶段与模板项与有效证据条件计数），避免 N+1 次单项目聚合调用。  
- **响应**：在现有 ProjectVO 或列表项 DTO 上增加 evidenceCompletionPercent、keyMissingSummary 字段；若某项目无任何阶段数据（如老数据），可返回 null 或 100 与空数组，与前端约定一致即可。

---

## 六、回归测试用例清单（必须覆盖）

### 6.1 has_procurement 与 required_when

- **用例 1**：project.has_procurement = false，S1 阶段仅含 3 项参与计算（S1_PRODUCT_COMPARE 被剔除）；阶段完成度与 overall% 分母为 3；S1 完成 3 项即可标记 S1 完成。  
- **用例 2**：project.has_procurement = true，S1 含 4 项参与计算；S1_PRODUCT_COMPARE 未上传时，keyMissing 含该项，阶段完成与归档门禁不通过。  
- **用例 3**：has_procurement 从 true 改为 false 后，聚合接口中 S1 项数从 4 变为 3，且 S1_PRODUCT_COMPARE 不再出现在 keyMissing。

### 6.2 S5 二选一

- **用例 4**：S5 仅上传验收报告（S5_ACCEPTANCE_REPORT）且有效证据数≥1，S5 阶段 x=1、y=1，组通过；归档门禁中 S5 通过。  
- **用例 5**：S5 仅上传终验报告（S5_FINAL_REVIEW_REPORT）且有效证据数≥1，同上，S5 通过。  
- **用例 6**：S5 两项均未上传或均为草稿，S5 组不通过；归档返回 400，keyMissing 含验收相关提示，archiveBlockReason 明确。

### 6.3 有效证据口径

- **用例 7**：某类型仅存在 DRAFT 或 INVALID 证据，currentCount=0，该项未完成，参与 keyMissing 与门禁。  
- **用例 8**：某类型存在 1 条 SUBMITTED，min_count=1，该项完成；再作废该证据后，currentCount=0，该项未完成，keyMissing 与门禁反映缺失。

### 6.4 归档门禁失败提示

- **用例 9**：缺任意必填项时，POST archive 返回 400，body 中 keyMissing 非空、archiveBlockReason 为非空字符串。  
- **用例 10**：某阶段完成度未达 100% 时，POST archive 返回 400，提示与缺失项一致。  
- **用例 11**：全部满足时，POST archive 返回 200，project.status 变为 archived。

### 6.5 x/y 与 overall% 一致性

- **用例 12**：GET stage-progress 返回的各阶段 itemCount/completedCount 与 items[].currentCount/completed 一致；各阶段 completionPercent 与 itemCount、completedCount 计算一致。  
- **用例 13**：overallCompletionPercent 与各阶段 x、y 汇总结果一致（总完成项/总项数×100）。  
- **用例 14**：GET /api/projects 列表中某项目的 evidenceCompletionPercent 与 GET stage-progress 对该 projectId 的 overallCompletionPercent 一致；keyMissingSummary 为 keyMissing 的子集（如前 3–5 条）。

### 6.6 按阶段+模板项证据列表

- **用例 15**：GET `/api/projects/{id}/stages/S2/evidence-types/S2_ARRIVAL_PHOTO/evidences` 仅返回该项目、S2 阶段、S2_ARRIVAL_PHOTO 类型的证据实例；含 DRAFT/SUBMITTED/ARCHIVED/INVALID 均可返回；每条含 evidenceStatus、latestVersion 等约定字段。

---

## 七、实现口径细节（开工前拍板，与接口契约一致）

以下 4 点在编码前定死，后端实现与前端契约均按此执行。

### 7.1 stage-progress 的 items[]：模板行列表 + 组级状态表达

- **拍板**：**items[] 为“模板行列表”**（即每行 stage_evidence_template 一条，不按 rule_group 折成一条）。S5 二选一在 items[] 中会看到两行（S5_ACCEPTANCE_REPORT、S5_FINAL_REVIEW_REPORT），每行均有 ruleGroup、completed（行级：该 type 的 currentCount≥min_count）、以及**组级状态**。
- **组级状态表达方式**（二选一，实现时择一）：  
  - **方案 A**：在 **items[] 每行上** 增加 **groupCompleted**（boolean，若该行属某 rule_group，则为该组是否通过；不属组则为 null 或与 completed 一致）、**groupDisplayName**（string | null，若属组则为整组展示名，如“验收报告（签字版）或终验专家评审报告（签字版）”，供前端将两行合并展示为一个组）。  
  - **方案 B**：在 **stages[] 每阶段** 增加 **groups[]**，每元素含 ruleGroup、groupDisplayName、groupCompleted、evidenceTypeCodes[]（组内类型列表）；items[] 仍为模板行列表，前端通过 ruleGroup 关联到 groups[] 做展示。  
- **推荐**：**方案 A**（在 items 上提供 groupCompleted、groupDisplayName），前端无需再解析 groups，直接按行渲染，同 ruleGroup 且同 groupDisplayName 的行可折叠为“一组”。  
- **约定**：同一 rule_group 内所有行的 groupDisplayName 相同；groupCompleted 对组内每一行返回值相同（整组通过则都为 true，否则都为 false）。

### 7.2 上传接口对 required_when 的处理

- **拍板**：**允许上传任意模板项**（只要 evidence_type_code 属于该阶段的模板即可），**不**在上传时校验 required_when。  
- **统计/门禁规则**：当 **required_when 不满足**（如 HAS_PROCUREMENT 且 project.has_procurement=false）时，**该模板项不参与** y、必填、阶段完成、归档门禁、keyMissing；**已上传的该类型证据仍存库**，但**不计入 currentCount、不进入 keyMissing**。即：允许先上传“比测报告”，等 has_procurement 改为 true 后，该证据自动参与统计与门禁。  
- **切换 has_procurement 后统计变化**：  
  - **false → true**：S1 参与计算项从 3 变为 4（S1_PRODUCT_COMPARE 加入）；若该项目已上传过 S1_PRODUCT_COMPARE 且有效，则 S1 的 x 与 overall% 会上升；若未上传或仅草稿，则 keyMissing 会多出该项。  
  - **true → false**：S1 参与计算项从 4 变为 3；S1_PRODUCT_COMPARE 已上传的证据仍保留，但不再计入 y/x、不进入 keyMissing；overall% 分母减少，可能升高或不变（视其它阶段而定）。

### 7.3 阶段“完成”的展示口径

- **拍板**：  
  - **阶段是否显示“✅完成”**：以 **project_stage.status = 已完成** 为准（即用户点击“标记阶段完成”且门禁通过后的状态）。  
  - **x==y** 仅表示该阶段**满足完成条件**，即 **canComplete=true**；**不**表示阶段已显示为完成。  
  - 当 **x==y 但 project_stage.status ≠ 已完成** 时，前端展示为**“可完成但未完成”**（或“满足条件，请点击完成”等）；仅当 status=已完成 时展示“✅完成”。  
- **接口约定**：GET stage-progress 的 stages[] 中每阶段需返回 **stageCompleted**（boolean），取值为 project_stage.status == 已完成；可选返回 **canComplete**（boolean），取值为 x==y。前端用 stageCompleted 控制✅展示，用 canComplete 控制“标记完成”按钮可用与否。

### 7.4 归档门禁失败的结构化返回

- **拍板**：除 **archiveBlockReason**（string）外，归档失败（400）时 body 必须包含以下**结构化字段**，便于前端做提示与跳转定位：  
  - **blockedByStages**：string[]，未达到 100% 完成度或未“标记完成”的阶段 code 列表（如 ["S2","S5"]）。前端可用于“某阶段未完成”提示及定位到该阶段 Tab/折叠块。  
  - **blockedByRequiredItems**：array of { stageCode?: string, evidenceTypeCode?: string, displayName: string, shortfall?: number }（或等价结构）。未满足的必填项列表；shortfall 表示还差几份（currentCount 与 min_count 的差）。前端可用于“缺少验收报告”等文案及定位到阶段+模板项。  
  - **keyMissing**：string[]，保留，为 blockedByRequiredItems 的 displayName 列表（或组代表展示名），与现有约定一致。  
- **约定**：blockedByStages 与 blockedByRequiredItems 可同时存在；例如某阶段 x<y 导致未完成，则该 stageCode 进入 blockedByStages，且该阶段内未满足的必填项进入 blockedByRequiredItems；若某阶段 x==y 但未点击完成（project_stage 未完成），仅该 stageCode 进入 blockedByStages，该阶段不必填项缺失则不进入 blockedByRequiredItems。

---

## 八、实施顺序建议（供编码阶段参考）

1. **数据与脚本**：编写 dev-reset.sql（清库）；编写 Flyway 迁移 V13（delivery_stage、project_stage、stage_evidence_template 表结构 + 固定数据；evidence_item 增 stage_id、evidence_type_code，删 status、biz_type；project 增 has_procurement；delivery_stage 含 description）。  
2. **实体与 Mapper**：新增/调整 Entity、Mapper 与 XML（阶段、模板、项目阶段进度；evidence_item、project 字段调整）；证据统计按 (project_id, stage_id, evidence_type_code) 与 evidence_status 的查询/聚合。  
3. **统计与门禁服务**：实现“参与计算项过滤 → 有效证据计数 → 单项/组完成判定 → x/y、overall%、keyMissing、canArchive、archiveBlockReason”的统一逻辑，供聚合接口、阶段完成、项目归档、列表扩展共用。  
4. **接口**：实现 GET stage-progress、GET 按阶段+模板项证据列表、POST 阶段完成、POST 项目归档；扩展 GET /api/projects 的 evidenceCompletionPercent、keyMissingSummary。  
5. **上传与列表**：上传接口增加 stage_id、evidence_type_code 并校验；证据列表与详情去掉 status、bizType，返回 evidenceStatus、stageId、evidenceTypeCode。  
6. **项目创建与初始化**：项目创建/导入时写入 has_procurement；创建后初始化 project_stage 五条。  
7. **回归**：按第六节用例清单执行自测，并补充必要单元测试（统计与门禁逻辑、required_when、rule_group）。

---

以上为《后端实施计划》全文。确认后进入实际代码实现与自测阶段。
