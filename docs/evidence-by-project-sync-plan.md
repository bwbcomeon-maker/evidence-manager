# 「按项目查看证据」与项目证据管理同步 — 核查说明与调整计划

## 一、前期实现方式核查

### 1.1 「按项目查看证据」入口与流程

| 环节 | 文件/路由 | 实现方式 |
|------|-----------|----------|
| 入口 | 证据管理首页 →「按项目查看证据」 | `EvidenceHome.vue` / `Evidence.vue` 中 `<van-cell title="按项目查看证据" is-link to="/evidence/by-project" />` |
| 项目列表页 | `/evidence/by-project` | **EvidenceByProject.vue**：调用 `getProjects()` 拉取项目列表，展示项目名称、描述、状态（进行中/已归档）；点击项目 → `router.push(\`/projects/${projectId}/evidences\`)` |
| 证据列表页 | `/projects/:id/evidences` | **EvidenceList.vue**（`views/EvidenceList.vue`）：**仅使用 Mock 数据**，未对接任何证据 API；三条写死数据，状态为 `active`/`archived`；点击证据项仅 `console.log`，无跳转详情 |

### 1.2 项目证据管理（当前主流程）

| 环节 | 文件/路由 | 实现方式 |
|------|-----------|----------|
| 入口 | 项目 Tab → 项目列表 → 点击某项目 | 进入 **ProjectDetail.vue** ` /projects/:id` |
| 项目详情 | 详情 Tab (activeTab=0) | 项目基础信息 + 证据完成度、关键缺失、申请归档（与阶段进度一致） |
| 证据 Tab (activeTab=1) | 同一页面 | **阶段驱动**：阶段进度条、阶段折叠、按阶段+模板项展示证据（displayName、uploadCount/minCount、completed）、证据实例网格（缩略图/文件卡片 + 上传入口）；数据来源为 `getStageProgress(projectId)`、`getEvidencesByStageType(projectId, stageCode, evidenceTypeCode)`；支持预览/下载/提交/作废 |

---

## 二、两者关系与现状问题

- **关系**：「按项目查看证据」的第二步（选择项目后看到的“证据列表”）与「项目详情 → 证据 Tab」在业务上都是“按项目看证据”，但实现上完全脱节。
- **问题**：
  1. 从「按项目查看证据」点进项目后，进入的是 **独立页面** `/projects/:id/evidences`（EvidenceList），该页 **未对接真实 API**，为 Mock，且状态与当前证据模型（DRAFT/SUBMITTED/ARCHIVED/INVALID）不一致。
  2. 真正的项目证据能力（阶段、模板项、完成度、上传入口、门禁与归档）全部在 **ProjectDetail 证据 Tab** 中实现，用户从「按项目查看证据」无法进入该视图，造成入口与主流程不一致、功能未同步。

---

## 三、调整目标

- 使「按项目查看证据」与**当前项目证据管理**一致：用户从证据管理 → 按项目查看证据 → 选择项目后，应进入**同一套阶段驱动证据视图**（项目详情页证据 Tab），而不是旧的 Mock 证据列表页。
- 避免维护两套“项目下证据”的展示与接口，以 **stage-progress + 按阶段/模板项证据列表** 为唯一事实源。

---

## 四、调整计划

### 4.1 方案 A（推荐）：统一跳转到项目详情证据 Tab

| 步骤 | 说明 |
|------|------|
| 1 | **EvidenceByProject.vue**：将「点击项目」的跳转改为 **`router.push({ path: \`/projects/${projectId}\`, query: { tab: 'evidence', from: 'evidence-by-project' } })`**，进入项目详情页并默认打开「证据」Tab；`from=evidence-by-project` 用于返回时识别来源。 |
| 2 | **MainLayout.vue**：项目详情页点击「返回」时，若 `query.from === 'evidence-by-project'` 则 **`router.replace('/evidence/by-project')`**（回到按项目查看证据列表），否则 `router.replace('/projects')`（回到项目列表）。这样从证据管理进入的用户可**一键返回到证据管理**。 |
| 3 | **路由**：`/projects/:id/evidences` 配置为 **redirect** 到 `/projects/:id?tab=evidence`，书签/外链仍可用。 |
| 4 | **可选**：在 EvidenceByProject 项目列表中展示「证据完成度」或「关键缺失条数」（列表接口已返回 `evidenceCompletionPercent`、`keyMissingSummary`），与项目列表页、项目详情页口径一致。 |

**优点**：零重复实现，用户从两个入口看到的项目证据一致；从「按项目查看证据」进入详情后，点返回可**直接回到「按项目查看证据」列表**，再点一次返回可回到证据管理首页。

### 4.2 方案 B（备选）：保留独立证据列表页并改为阶段驱动

若产品必须保留「证据列表」独立 URL（如 `/projects/:id/evidences` 作为独立页面）：

| 步骤 | 说明 |
|------|------|
| 1 | **EvidenceList.vue** 重写：去掉 Mock，调用 `getStageProgress(projectId)`、按阶段/模板项调用 `getEvidencesByStageType`，展示结构对齐 ProjectDetail 证据 Tab（阶段折叠 + 模板项 + 证据实例网格）。 |
| 2 | 状态与交互与 ProjectDetail 证据 Tab 统一：状态用 DRAFT/SUBMITTED/ARCHIVED/INVALID 及现有工具函数；列表项可跳转证据详情 `/evidence/detail/:id`。 |
| 3 | 上传入口：可在此页提供「进入项目详情上传」的引导，或复用 ProjectDetail 内上传逻辑（需抽组件或复用 API）。 |

**缺点**：需维护两处阶段驱动 UI 与接口调用，容易不同步；推荐仅在确有独立 URL 需求时采用。

---

## 五、推荐执行顺序（按方案 A）

1. **修改 EvidenceByProject.vue**：`goToProjectEvidences(projectId)` 改为跳转 `/projects/${projectId}?tab=evidence`。
2. **路由调整**：`/projects/:id/evidences` 重定向到 `/projects/:id?tab=evidence`（或 EvidenceList 组件内 redirect）。
3. **可选增强**：EvidenceByProject 列表项展示 `evidenceCompletionPercent` / `keyMissingSummary`（与项目列表、详情一致）。
4. **测试**：从证据管理 → 按项目查看证据 → 选项目，确认进入项目详情且默认在证据 Tab，阶段、模板项、证据列表与从项目 Tab 进入一致。

---

## 六、涉及文件一览

| 文件 | 方案 A 变更 |
|------|-------------|
| `frontend/src/views/evidence/EvidenceByProject.vue` | 跳转改为 `path: /projects/:id`, `query: { tab: 'evidence' }`；可选展示完成度/关键缺失 |
| `frontend/src/router/index.ts` | `/projects/:id/evidences` 增加 redirect 到 `/projects/:id?tab=evidence`（或保留路由、EvidenceList 内 redirect） |
| `frontend/src/views/EvidenceList.vue` | 方案 A 下可改为仅做 redirect，或删除该路由由 redirect 替代 |

以上为核查结论与推荐调整计划，按方案 A 实施即可使「按项目查看证据」与当前项目证据管理同步。
