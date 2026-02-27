# 全局证据搜索功能实现方案评估报告

## 一、后端表关联现状分析

### 1.1 核心表与关联字段

| 表名 | 说明 | 与证据的关联方式 |
|------|------|------------------|
| **evidence_item** | 证据元数据表 | 主表。字段：`id`, `project_id`, `stage_id`, `evidence_type_code`, `title`, `note`, `content_type`, `evidence_status`, `created_by_user_id`, `created_at`, `invalid_by_user_id`, `invalid_at` 等。 |
| **project** | 项目表 | `evidence_item.project_id` → `project.id`。项目表字段：`id`, `code`, `name`, `description`, `status`, `created_by_user_id`。 |
| **sys_user** | 系统用户表 | `evidence_item.created_by_user_id` → `sys_user.id`；`evidence_item.invalid_by_user_id` → `sys_user.id`。用户表字段：`id`, `username`, `real_name`, `role_code` 等。 |
| **evidence_version** | 证据版本表 | `evidence_version.evidence_id` → `evidence_item.id`；`evidence_version.uploader_user_id` 对应上传人。用于取最新版本文件名等。 |
| **delivery_stage** | 阶段定义表 | `evidence_item.stage_id` → `delivery_stage.id`。阶段表字段：`id`, `code`, `name`。用于列表/详情中展示阶段编码（如 S1）和名称，以及**项目详情页“定位到具体证据项”所需的 stageCode**。 |

### 1.2 关联关系小结

- **证据 → 项目**：`evidence_item.project_id` = `project.id`（多对一）。
- **证据 → 上传人**：`evidence_item.created_by_user_id` = `sys_user.id`（多对一）；展示名取 `sys_user.real_name` 或 `sys_user.username`。
- **证据 → 阶段**：`evidence_item.stage_id` = `delivery_stage.id`（多对一）；`delivery_stage.code` 即前端需要的 `stageCode`（如 S1、S5）。
- **证据 → 最新版本**：通过 `evidence_version.evidence_id` 关联，按 `evidence_id` 取最新一条版本（版本号最大或 `created_at` 最大）。

当前分页列表接口 `EvidenceService.pageEvidence` 已使用 `getVisibleProjectIds` 做可见项目过滤，仅查询当前用户可见项目内的证据；全局搜索需沿用同一套“可见项目”逻辑，在 `project_id IN (visibleIds)` 前提下再做关键字匹配。

---

## 二、后端接口设计建议

### 2.1 统一返回格式

- 成功：`Result.success(data)`，即 `{ code: 0, message: "success", data: T }`。
- 失败：`Result.error(code, message)` 或 `Result.error(code, message, data)`（如 400 带业务数据）。
- 分页：`PageResult<T>`：`{ total, records, page, pageSize }`。

### 2.2 全局搜索接口建议

**方案 A：复用现有 GET /api/evidence 并扩展参数（推荐）**

- **路径**：保持 `GET /api/evidence`。
- **现有参数**：`page`, `pageSize`, `projectId`, `status`, `uploader`, `recentDays`, `fileCategory`, **`nameLike`**（已支持标题模糊，见 `EvidenceItemMapper.xml` 中 `PageWhereClause` 的 `nameLike`）。
- **新增参数**：
  - **`keyword`**（可选）：全局关键字。语义为“证据标题或上传人姓名/账号”的模糊匹配。
- **实现逻辑**：
  - 若只传 `nameLike`：保持现有行为（仅按 `evidence_item.title` 模糊，现有 SQL 已支持）。
  - 若传 `keyword`：  
    - 先按 `keyword` 在 `sys_user` 中查 `real_name ILIKE %keyword%` 或 `username ILIKE %keyword%`，得到 `created_by_user_id` 列表；  
    - 再在 `evidence_item` 上做：`(title ILIKE %keyword%) OR (created_by_user_id IN (上传人id列表))`，且 `project_id IN (visibleIds)`，其余过滤与现有分页一致（status、projectId 等）。  
  - 若同时传 `nameLike` 与 `keyword`，建议后端约定以 `keyword` 为准或两者合并为 OR 条件，避免歧义。
- **返回**：`Result<PageResult<EvidenceListItemVO>>`。  
  - **关键**：为支持前端“跳转项目详情并定位到证据项”，`EvidenceListItemVO` 在全局搜索场景下**必须带**：`projectId`（已有）、`stageCode`、`stageName`、`evidenceTypeCode`、`evidenceTypeDisplayName`（已有或可补）。  
  - 当前 `pageEvidence` 未填充 `stageCode`/`stageName`/`evidenceTypeDisplayName`，需在 Service 层根据 `evidence_item.stage_id`、`evidence_type_code` 查 `delivery_stage` 与 `stage_evidence_template` 并 set 到 VO，或在新接口/新方法中单独写一套组装逻辑。

**方案 B：新建专用接口 GET /api/evidence/search**

- **路径**：`GET /api/evidence/search?keyword=&page=&pageSize=`。
- **入参**：`keyword`（必填或至少一个），`page`，`pageSize`；可选保留 `status`、`projectId` 等与现有一致。
- **出参**：`Result<PageResult<EvidenceSearchResultVO>>`。
- **EvidenceSearchResultVO**：与 `EvidenceListItemVO` 对齐字段，但明确包含：  
  `evidenceId`, `projectId`, `projectName`, `stageCode`, `stageName`, `evidenceTypeCode`, `evidenceTypeDisplayName`, `title`, `createdByDisplayName`, `createdAt`, `evidenceStatus`, `latestVersion`（可选）。  
  - 这样前端“搜索 → 点结果 → 进项目详情并定位”只需一套 VO，无需再区分列表/搜索两种结构。

### 2.3 MyBatis 层实现建议

- **关键字匹配上传人**：  
  - 方式 1：在 Java 中先 `sysUserMapper.selectByRealNameOrUsernameLike(keyword)` 得到 `List<Long> userIds`，再在 `selectPageWithFilters` 中增加 `createdByUserId IN (userIds)` 或新增 `createdByUserIds` 参数；若 `userIds` 为空则仅用 `title ILIKE`。  
  - 方式 2：在 Mapper 中写一条新 SQL，`evidence_item` LEFT JOIN `sys_user` ON `created_by_user_id = sys_user.id`，WHERE 中 `(title ILIKE #{keyword}) OR (sys_user.real_name ILIKE #{keyword}) OR (sys_user.username ILIKE #{keyword})`，并保留 `project_id IN (visibleIds)` 及现有 status 等条件。  
- **stageCode / stageName**：  
  - 在 SQL 中 LEFT JOIN `delivery_stage` ON `evidence_item.stage_id = delivery_stage.id`，SELECT 增加 `delivery_stage.code AS stage_code`, `delivery_stage.name AS stage_name`；  
  - 或在查出 `EvidenceItem` 后，在 Service 中按 `stageId` 批量查 `DeliveryStage` 再组装到 VO。  
- **evidenceTypeDisplayName**：  
  - 同理，需 `stage_evidence_template` 表（或等价配置）按 `evidence_type_code`（及可选 `stage_id`）取展示名，在 Service 或 SQL 中补齐。

### 2.4 权限与数据范围

- 全局搜索必须沿用 **getVisibleProjectIds(currentUserId, roleCode)**：只查可见项目内的证据，与现有列表、详情一致。
- 不做“可见项目”限制会带来越权风险。

---

## 三、前端跨页定位难点排查

### 3.1 路由与页面关系

- **证据管理首页**：`/evidence`（EvidenceHome），含全局搜索框与搜索结果列表（当前为 Mock）。
- **项目详情页**：`/projects/:id`（ProjectDetail），带 Tab：基本信息(0)、证据(1)。证据 Tab 内为 `van-collapse`（按阶段折叠），每个阶段下为 `evidence-card` 列表，每个卡片有 **`:id="'evidence-card-' + s.stageCode + '-' + item.evidenceTypeCode"`**。
- **证据详情页**：`/evidence/detail/:id`（EvidenceDetail），仅展示单条证据，不涉及“定位到项目内某证据项”。

目标：从搜索页点击某条结果 → 跳转到**项目详情页**的**证据 Tab**，并**滚动到该证据所在卡片**（即“定位到具体证据项”）。

### 3.2 当前“定位到证据项”的已有能力（项目详情页内）

- **关键缺失**点击：`scrollToEvidence(entry)`，入参为 `{ stageCode, evidenceTypeCode }`。  
  流程：若当前不在证据 Tab 则 `activeTab = 1` → `nextTick` → 展开对应 `van-collapse`（`expandedStages` 加入 `stageCode`）→ 再次 `nextTick` → `document.getElementById('evidence-card-' + stageCode + '-' + evidenceTypeCode)` → `scrollIntoView({ behavior: 'smooth', block: 'center' })` → 添加高亮 class。
- 项目详情页**已支持**通过 **query** 恢复状态：  
  - `tab=evidence`：onMounted 与 watch 会把 `activeTab` 设为 1；  
  - `expandedStage=xxx`：watch 会在 `stageProgress` 加载后把 `expandedStages` 加入对应阶段。  
- **未支持**：进入时根据 query 自动执行“滚动到某 evidence-card 并高亮”。当前仅有关键缺失的点击会触发 `scrollToEvidence`，没有“从 URL 读取 scrollTarget 并执行”的逻辑。

### 3.3 从搜索页跳转到项目详情并定位的可行方案

- **方式 1：通过 query 传定位参数，项目详情页在合适的时机调用 scrollToEvidence**  
  - 搜索结果点击：`router.push({ path: '/projects/' + projectId, query: { tab: 'evidence', scrollStage: stageCode, scrollType: evidenceTypeCode } })`。  
  - 项目详情页需：  
    - 在 `stageProgress` 加载完成且已展开 `scrollStage` 后，再执行一次“滚动 + 高亮”（即复用 `scrollToEvidence` 的逻辑，入参为 `{ stageCode: scrollStage, evidenceTypeCode: scrollType }`）。  
  - **难点**：  
    - `stageProgress` 与 `loadAllItemEvidences` 为异步；若在 `onMounted` 里只做一次 `nextTick` 再执行滚动，可能此时 DOM 尚未渲染完（例如折叠刚展开，证据列表仍在加载）。  
    - 需要在“阶段已展开 + 对应 evidence-card 的 DOM 已存在”后再执行 `scrollIntoView`，否则 `getElementById` 为空。建议：在 watch `[stageProgress, expandedStages]` 或 `loadAllItemEvidences` 完成后的某处（例如在 `expandedStages` 包含 `scrollStage` 且 `stageProgress` 已有数据时，用 `nextTick` 或 `setTimeout` 延迟一次）再执行滚动并高亮，执行后清除 query 中的 `scrollStage`/`scrollType`，避免重复滚动。  
  - **v-if 影响**：证据 Tab 内容在 `activeTab === 1` 时才渲染，`van-collapse` 的展开又依赖 `expandedStages`。因此“从 URL 驱动滚动”必须在 Tab 已切到证据且对应阶段已展开、且 evidence-card 已渲染的前提下执行，否则会受 v-if 和异步数据影响导致取不到 DOM。

- **方式 2：先跳证据详情再“去项目详情”**  
  - 点击结果先进 `/evidence/detail/:id`，详情页提供“在项目内定位”按钮，跳转到 `/projects/:projectId?tab=evidence&scrollStage=...&scrollType=...`。  
  - 用户体验多一步，但实现简单，且不依赖“进入项目详情时异步加载完成”的时机。

推荐：**方式 1**，在项目详情页增加对 `query.scrollStage` + `query.scrollType` 的监听，在“证据 Tab 已激活 + 对应阶段已展开 + 至少一次 nextTick/requestAnimationFrame”后执行滚动与高亮，并清掉 query，避免与现有逻辑冲突。

### 3.4 生命周期与 v-if 对平滑滚动的影响

- **Tab 切换**：`activeTab` 从 0 改为 1 时，证据区域由 v-if 从无到有渲染；若不等待 `nextTick`，DOM 可能尚未存在。  
- **折叠展开**：`expandedStages` 变化后，`van-collapse` 内部高度会变化，evidence-card 的渲染也可能在下一帧。  
- **证据列表按项加载**：`loadEvidenceForItem` 按阶段+类型异步拉取，`evidence-card` 的 DOM 在数据返回后才会出现；若在数据未返回前就执行 `getElementById`，会得到 null。  
- **建议**：  
  - 先确保 `tab=evidence` 已生效且 `expandedStages` 已包含目标阶段；  
  - 再在“阶段展开”后的下一次 `nextTick` 中尝试 `getElementById`；若仍为空，可短延时（如 100–300ms）再试一次，或监听 `evidenceByItemMap` 中对应 key 已有数据后再执行滚动，以规避“列表未加载完就滚动”的问题。

---

## 四、风险提示

1. **权限一致性**  
   全局搜索必须与现有列表、详情使用同一套“可见项目”规则（getVisibleProjectIds）。若新接口或新 SQL 漏掉“仅限可见项目”，会导致越权看到其他项目证据。

2. **关键字搜上传人**  
   - 若用“先查用户 ID 再查证据”的方式，需注意 `sys_user` 中 `is_deleted` 等状态，避免把已逻辑删除用户关联的证据漏掉或误过滤。  
   - 若用 JOIN，需注意 LEFT JOIN 与 WHERE 组合，避免因 NULL 导致结果被过滤。

3. **项目详情页 from 与只读**  
   - 项目详情页有 `route.query.from === 'evidence-by-project'` 时，会隐藏删除/上传等操作。  
   - 从全局搜索跳转到项目详情时，若带 `from=global-search` 或类似标识，需确认是否要沿用“只读”展示（通常从搜索进项目详情应允许操作，故可不带 `from=evidence-by-project`）。

4. **stageProgress 与 stageCode 的时序**  
   - 项目详情页的 `stageProgress` 在 `loadStageProgress()` 完成后才有 `stages`；`expandedStage` 的 watch 依赖 `stageProgress.value`。  
   - 若用户直接打开 `/projects/123?tab=evidence&scrollStage=S1&scrollType=xxx`，需保证先 `loadStageProgress()`，再在 watch 中展开 S1，再在合适的时机执行滚动，避免在 `stageProgress` 为空时就用 `scrollStage` 去展开，导致无效或报错。

5. **EvidenceListItemVO 与搜索 VO**  
   - 当前 `EvidenceListItemVO` 在 `pageEvidence` 中未填充 `stageCode`、`stageName`、`evidenceTypeDisplayName`；若全局搜索直接复用该 VO 且前端依赖这些字段做“跳转并定位”，后端必须补全这些字段，否则前端无法构造 `scrollStage`/`scrollType`。

6. **Mock 与真实接口切换**  
   - 证据管理首页当前为 Mock 数据，evidenceId 等为假数据；接入真实全局搜索接口后，需统一使用后端返回的 `projectId`、`stageCode`、`evidenceTypeCode`、`evidenceId`，并确保点击结果时跳转 `/projects/:projectId?tab=evidence&scrollStage=...&scrollType=...`，且项目详情页已实现“按 query 自动滚动并高亮”的逻辑。

---

## 五、总结与建议顺序

1. **后端**  
   - 在现有 `getVisibleProjectIds` 与分页过滤基础上，增加“关键字”语义：支持按证据标题 **或** 上传人（real_name/username）模糊匹配；  
   - 全局搜索（或扩展后的 list）返回的每条证据带齐：`projectId`、`projectName`、`stageCode`、`evidenceTypeCode`、`evidenceTypeDisplayName`、`evidenceId`、`title`、`createdByDisplayName`、`createdAt`、`evidenceStatus`，便于前端展示与跳转。

2. **前端·搜索页**  
   - 将 Mock 替换为真实接口（如 GET /api/evidence?keyword=xxx&page=1&pageSize=20）；  
   - 点击结果时跳转：`/projects/${projectId}?tab=evidence&scrollStage=${stageCode}&scrollType=${evidenceTypeCode}`（不带 `from=evidence-by-project`），以便在项目详情页内定位到具体证据项。

3. **前端·项目详情页**  
   - 增加对 `route.query.scrollStage`、`route.query.scrollType` 的处理：在证据 Tab 已激活、对应阶段已展开、且 DOM 已就绪（nextTick + 必要时短延时或依赖 evidenceByItemMap 数据）后，执行与 `scrollToEvidence` 相同的滚动与高亮逻辑，执行后清除 query 中的定位参数，避免刷新或返回时重复滚动。

按上述顺序实现，可满足“全局证据搜索 + 跨页面跳转到项目详情并定位到具体证据项”的需求，并与现有权限、路由和组件结构保持一致。
