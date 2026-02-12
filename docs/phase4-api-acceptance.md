# Phase 4 接口验收说明（阶段驱动证据联调）

后端以 **GET /api/projects/{projectId}/stage-progress** 为唯一事实源，不改变统计口径与门禁算法。本文档与 `scripts/phase4-api-acceptance.sh` 配套使用。

## 环境准备

- 后端已启动（如 `http://localhost:8081`）
- 已登录：脚本使用 Cookie 会话，请先浏览器登录或执行登录接口后把 Cookie 写入 `scripts/.cookie`，或导出为环境变量（见脚本内说明）
- 脚本依赖 `jq`（用于解析 JSON 与检查字段），执行前请安装：`brew install jq`（macOS）或各系统包管理器

## 接口清单与预期检查点

### 1. GET /api/projects（列表扩展）

**路径**: `GET /api/projects`

**检查点**:
- HTTP 200，`code === 0`
- `data` 为数组，每项为项目对象
- **列表扩展**：每项须包含：
  - `evidenceCompletionPercent`: number（0–100）
  - `keyMissingSummary`: string[]（最多约 5 条关键缺失摘要，可为空数组）

**示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "code": "PROJ-001",
      "name": "示例项目",
      "evidenceCompletionPercent": 60,
      "keyMissingSummary": ["S1-启动会照片", "S2-物流签收单"]
    }
  ]
}
```

---

### 2. GET /api/projects/{projectId}/stage-progress（唯一事实源）

**路径**: `GET /api/projects/{projectId}/stage-progress`

**检查点**:
- HTTP 200，`code === 0`
- `data` 包含：
  - `overallCompletionPercent`: number
  - `keyMissing`: string[]
  - `canArchive`: boolean
  - `archiveBlockReason`: string | null（不可归档时由后端填充）
  - `stages`: Array<{ stageCode, stageName, itemCount, completedCount, completionPercent, healthStatus, stageCompleted, canComplete, items }>
  - `projectName`, `projectStatus`, `hasProcurement`
  - `blockedByStages`: string[]（不可归档时有值）
  - `blockedByRequiredItems`: Array<{ stageCode, evidenceTypeCode, displayName, shortfall }>（不可归档时有值）
- `stages[].items[]` 每项包含：`evidenceTypeCode`, `displayName`, `required`/`isRequired`, `minCount`, `currentCount`, `completed`, `ruleGroup`, `groupCompleted`, `groupDisplayName`

---

### 3. GET /api/projects/{projectId}/stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences

**路径**: `GET /api/projects/1/stages/S1/evidence-types/S1_START_PHOTO/evidences`（示例）

**检查点**:
- HTTP 200，`code === 0`
- `data` 为证据实例数组
- 每项包含：`evidenceId`, `projectId`, `stageId`, `stageCode`, `evidenceTypeCode`, `title`, `evidenceStatus`, `createdByDisplayName`, `latestVersion`, `permissions` 等

---

### 4. POST /api/projects/{projectId}/stages/{stageCode}/complete（阶段完成）

**成功**:
- HTTP 200，`code === 0`，`data.success === true`

**失败（门禁未过）**:
- HTTP 200 且 `code === 400`（或后端以 400 状态码返回，视实现而定）
- **Result 的 data 结构**（前端须能读取 `err.response.data.data`）:
  - `success`: false
  - `message`: string（如 "阶段未满足完成条件"）
  - `missingItems`: Array<{ stageCode, evidenceTypeCode, displayName, shortfall }>

**验收**：各验证一次成功与一次失败（如阶段未全部完成时调用）。

---

### 5. POST /api/projects/{projectId}/archive（项目归档）

**成功**:
- HTTP 200，`code === 0`

**失败（门禁未过）**:
- HTTP 200 且 `code === 400`（或 400 状态码）
- **Result 的 data 结构**（前端须能读取 `err.response.data.data`）:
  - `archiveBlockReason`: string
  - `keyMissing`: string[]
  - `blockedByStages`: string[]
  - `blockedByRequiredItems`: Array<{ stageCode, evidenceTypeCode, displayName, shortfall }>

**验收**：各验证一次成功与一次失败。

---

## 400 错误体统一约定

后端对阶段完成失败、归档失败返回 **HTTP 200 + body 中 code=400**（或 HTTP 400，依实现）。前端需：

1. 判断 `response.data.code === 400` 或 `error.response?.status === 400`
2. 从 **`response.data.data`**（或 `error.response.data.data`）读取结构化内容：
   - 阶段完成：`data.missingItems`, `data.message`
   - 归档：`data.archiveBlockReason`, `data.keyMissing`, `data.blockedByStages`, `data.blockedByRequiredItems`

脚本中会通过 jq 检查上述字段存在性。
