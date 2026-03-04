# 我的待办功能 — 总体梳理

> 从总体设计、数据库、前后端实现、未读/已读状态到数量提示的完整说明。

---

## 一、总体设计思路

### 1.1 业务定位

- **我的待办**：当前登录用户收到的、与归档审批相关的消息/待办列表。
- **核心能力**：
  - 按「接收人」隔离：每人只能看到 `user_id = 自己` 的待办。
  - 未读/已读：以 `read_at` 是否为空区分；未读条数用于「我的」页角标。
  - 点击跳转：每条待办带 `linkPath`，点击后先标记已读（可选）、再跳转（如项目证据页）。

### 1.2 与归档审批的关系

| 业务事件 | 谁收到待办 | 类型 type | 含义 |
|----------|-------------|-----------|------|
| 项目经理提交归档申请 | 所有 PMO、SYSTEM_ADMIN | ARCHIVE_PENDING | 待审批 |
| PMO/管理员审批通过 | 该申请的项目经理 | ARCHIVE_APPROVED | 归档已通过 |
| PMO/管理员退回 | 该申请的项目经理 | ARCHIVE_RETURNED | 归档已退回 |

设计上：**待办只做“谁该处理/被通知”，不参与审批业务逻辑**；未读状态仅影响展示与角标。

---

## 二、涉及的数据库

### 2.1 直接相关表

**`notification`（消息待办表）** — 迁移脚本：`V15__archive_approval_tables.sql`

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGINT 自增主键 | 消息 ID |
| user_id | BIGINT NOT NULL, FK → sys_user(id) | **接收人**，决定“我的待办”归属 |
| type | VARCHAR(32) NOT NULL | ARCHIVE_PENDING / ARCHIVE_APPROVED / ARCHIVE_RETURNED |
| title | VARCHAR(200) NOT NULL | 标题 |
| body | TEXT | 正文 |
| related_project_id | BIGINT, FK → project(id) | 关联项目 |
| related_application_id | BIGINT, FK → project_archive_application(id) | 关联申请单 |
| link_path | VARCHAR(500) | 前端跳转路径，如 `/projects/123?tab=evidence` |
| **read_at** | TIMESTAMPTZ NULL | **已读时间；NULL = 未读** |
| created_at | TIMESTAMPTZ NOT NULL | 创建时间 |

索引：`user_id`、`type`、`read_at`、`created_at DESC`，用于按用户、未读筛选和排序。

### 2.2 间接相关表

- **`sys_user`**：接收人/当前用户，Session 中的 userId 来源。
- **`project_archive_application`**：归档申请单；提交/通过/退回时写入 notification。
- **`project`**：项目状态与详情页展示，不直接存待办状态。

---

## 三、后端实现

### 3.1 接口一览

| 方法 | 路径 | 作用 |
|------|------|------|
| GET | /api/notifications/todos | 当前用户待办列表（支持 unreadOnly、type、limit） |
| PATCH | /api/notifications/{id}/read | 单条标记已读 |
| POST | /api/notifications/read | 批量标记已读（body: { ids: [1,2,3] }） |

当前端实际使用：**GET /todos**（列表 + 未读数量）、**PATCH /{id}/read**（点击某条时标记已读）。

### 3.2 待办列表：GET /api/notifications/todos

- **Controller**：`NotificationController.listTodos`  
  - 从 `AuthInterceptor.REQUEST_CURRENT_USER` 取当前用户，未登录返回 401。  
  - 调用 `notificationService.listTodos(user.getId(), unreadOnly, type, limit)`。
- **Service**：`NotificationServiceImpl.listTodos`  
  - 若 `userId == null` 返回空列表。  
  - 否则 `notificationMapper.selectByUserId(userId, unreadOnly, type, limit)`。
- **Mapper SQL**（`NotificationMapper.xml` — `selectByUserId`）：
  - `WHERE user_id = #{userId}`  
  - 若 `unreadOnly == true`：`AND read_at IS NULL`  
  - 可选 `AND type = #{type}`，`ORDER BY created_at DESC`，`LIMIT #{limit}`。
- **未读/已读判断**：**完全由数据库字段 `read_at` 决定**。  
  - `read_at IS NULL` → 未读；  
  - `read_at` 有值 → 已读。  
  列表接口只做查询，不修改状态。

### 3.3 单条标记已读：PATCH /api/notifications/{id}/read

- **Controller**：`NotificationController.markRead(request, id)`  
  - 取当前用户，调 `notificationService.markRead(userId, id)`。
- **Service**：`NotificationServiceImpl.markRead`  
  - 参数校验；`selectById(notificationId)` 不存在则 404。  
  - `notificationMapper.markReadByUser(notificationId, userId, now)`：  
    - SQL：`UPDATE notification SET read_at = #{readAt} WHERE id = #{id} AND user_id = #{userId}`  
  - 若 `updated == 0`（不是该条消息的接收人）→ 403。
- **状态更新**：仅此接口（及批量接口）会**写入** `read_at`，将未读改为已读。

### 3.4 待办的产生（写入 notification）

在 **ProjectArchiveServiceImpl** 中，与归档流程一起写入：

1. **apply（提交归档申请）**  
   - 查 PMO、SYSTEM_ADMIN 的 user_id 列表，对每个审批人插入一条 notification：  
     - `user_id = 审批人`，`type = ARCHIVE_PENDING`，`linkPath = /projects/{projectId}?tab=evidence`，**read_at 不设（NULL，即未读）**。

2. **approve（审批通过）**  
   - 给申请人插入一条：  
     - `user_id = app.getApplicantUserId()`，`type = ARCHIVE_APPROVED`，**read_at 为 NULL**。

3. **reject（退回）**  
   - 给申请人插入一条：  
     - `user_id = app.getApplicantUserId()`，`type = ARCHIVE_RETURNED`，**read_at 为 NULL**。

**结论**：所有新产生的待办初始都是**未读**（insert 时不写 read_at）。已读只能通过前端调用 PATCH `/api/notifications/{id}/read`（或后端批量接口）更新。

---

## 四、前端实现

### 4.1 API 封装（`frontend/src/api/notifications.ts`）

- **getTodos(params?)**  
  - `GET /notifications/todos`，params：`unreadOnly`、`type`、`limit`。  
  - 返回 `{ code, message, data: TodoItemVO[] }`，每条含 `id, type, title, body, relatedProjectId, relatedApplicationId, linkPath, readAt, createdAt`。
- **markTodoRead(id)**  
  - `PATCH /notifications/${id}/read`，无 body。  
  - 成功：后端把该条 `read_at` 更新为当前时间；前端用返回结果或本地状态更新 UI。

### 4.2 我的页面角标（Me.vue）

- **数据来源**：`unreadTodoCount = ref(0)`，在 **onMounted** 里请求一次：  
  - `getTodos({ unreadOnly: true })`，成功则 `unreadTodoCount.value = res.data.length`。
- **展示**：  
  - `v-if="unreadTodoCount > 0"` 时在「我的待办」右侧用 `van-badge` 显示数字（max 99）。  
  - 使用 `#right-icon` 插槽，避免角标被挤压变形。
- **何时“有”/“消失”**：  
  - **有**：进入「我的」页时，若当前用户有 `read_at IS NULL` 的待办，则数量 > 0，角标显示。  
  - **消失**：  
    - 用户去「我的待办」页点击某条 → 调用 `markTodoRead(id)` → 后端将该条置为已读 → 下次进入「我的」页时 `getTodos({ unreadOnly: true })` 的 `data.length` 少 1；若为 0 则角标不渲染（`unreadTodoCount > 0` 为 false）。  
  - 注意：「我的」页**不会**在从待办页返回时自动刷新；角标在**下次进入「我的」页**或**刷新**时才会更新。若希望从待办返回时角标立即更新，可在 Me 页用 onActivated 或路由守卫里再调一次 `getTodos({ unreadOnly: true })`（当前未实现）。

### 4.3 待办中心页（TodoCenter.vue）

- **列表数据**：  
  - 「全部待办」：`getTodos({ limit })`，不传 unreadOnly，后端返回该用户全部待办（含已读、未读）。  
  - 「未读待办」：`getTodos({ unreadOnly: true, limit })`，后端只返回 `read_at IS NULL` 的条数。
- **未读/已读在 UI 上的体现**：  
  - 每条 item 带 `readAt`（后端来自 `read_at`）。  
  - 列表项：`v-if="!item.readAt"` 时显示红点 `van-badge dot`，表示未读。  
  - 未读 tab 的列表本身已是“未读子集”，因此未读 tab 里每条都显示红点（逻辑上可不再判 readAt，当前实现仍带 `van-badge dot`）。
- **进入页面**：onMounted 时 `loadForCurrentUser()`：清空本地列表并重新拉取（全部或未读），保证是当前用户的列表；同时监听 `visibilitychange`，从其他标签切回时再拉一次，避免多标签/换账号后仍显示旧数据。

### 4.4 点击一条待办：标记已读 + 跳转

- **顺序**：先 `markTodoRead(item.id)`，成功后再 `router.push(item.linkPath)`（若有 linkPath）。
- **请求**：`PATCH /notifications/${id}/read`，**不传 body**；后端用路径里的 id + Session 中的 userId 更新 `read_at`。
- **成功时前端状态**：  
  - 若当前有 `!item.readAt` 且接口成功：  
    - `list` 中该条置为 `readAt: new Date().toISOString()`；  
    - 从 `listUnread` 中移除该条。  
  - 这样本页列表立即反映“已读”，无需重新请求列表。
- **失败时**：仍按 linkPath 跳转（若有），并 toast「已跳转」或错误信息；不更新本地 readAt/listUnread。

---

## 五、未读/已读状态：谁判断、谁更新

### 5.1 判断（数据源在后端）

- **唯一真相**：表 `notification.read_at`。  
  - `read_at IS NULL` → 未读。  
  - `read_at` 有值 → 已读。
- **前端**：  
  - 列表接口返回的每条 `TodoItemVO` 含 `readAt`（对应 `read_at`）。  
  - 未读数量 = `GET /todos?unreadOnly=true` 的 `data.length`，即后端按 `read_at IS NULL` 过滤后的条数。  
  前端不做“未读/已读”的独立计算，只展示后端结果。

### 5.2 状态更新（谁在何时改 read_at）

| 操作/变量 | 说明 |
|----------|------|
| **后端写入新待办** | apply/approve/reject 时 insert notification，不设 read_at → 新条均为未读。 |
| **用户点击某条待办** | 前端调 PATCH `/api/notifications/{id}/read` → 后端 `markReadByUser` 将该条 `read_at` 置为当前时间。 |
| **批量标记已读** | POST `/api/notifications/read` + body `{ ids: [1,2,3] }`，后端对每条若属于当前用户则更新 read_at。当前前端未使用该接口。 |

没有“自动已读”规则（例如打开详情就置已读）：**只有显式调用标记已读接口才会把未读改为已读**。

### 5.3 从前端到后端的传递与响应

- **标记已读**：  
  - 前端：`markTodoRead(id)` → HTTP `PATCH /api/notifications/{id}/read`，**仅路径参数 id**，无 body；Cookie 带 Session，后端从 Session 取当前 userId。  
  - 后端：用 `id` + `userId` 执行 `UPDATE notification SET read_at = now() WHERE id = ? AND user_id = ?`；返回 200 空 body 或统一成功结构。  
  - 前端：收到成功后再跳转并更新本地 list/listUnread（见上）。
- **列表与未读数量**：  
  - 前端：`getTodos({ unreadOnly: true })` 或 `getTodos({ limit })`。  
  - 后端：`WHERE user_id = #{userId}` 且可选 `AND read_at IS NULL`，返回带 `readAt` 的列表。  
  - 前端：用返回的 `data` 和 `data.length` 展示列表和角标，**不**把“未读/已读”再发回后端；后端只负责按当前数据返回。

---

## 六、小结

| 维度 | 要点 |
|------|------|
| **设计** | 待办按接收人隔离，未读/已读仅影响展示与角标；与归档业务解耦。 |
| **数据库** | 核心表 `notification`，`user_id` 表接收人，`read_at` 表未读/已读。 |
| **后端** | GET /todos 查列表（可 unreadOnly）；PATCH /{id}/read 单条置已读；新待办由归档流程 insert，初始未读。 |
| **前端** | Me 页 onMounted 用 unreadOnly 请求一次得到未读数并显示角标；TodoCenter 拉全部/未读列表，点击先 PATCH 再跳转并本地更新。 |
| **未读数量** | = GET /todos?unreadOnly=true 的 data.length；角标 `v-if="unreadTodoCount > 0"` 显示/消失。 |
| **未读已读判断** | 后端以 read_at 是否为空为准；前端仅展示接口返回的 readAt 与列表长度。 |
| **状态更新** | 仅“用户点击待办触发 PATCH”或“批量 POST”会更新 read_at；前端成功后在本地更新 list/listUnread。 |
| **前后端协作** | 标记已读：前端发 PATCH（带 id），后端用 Session userId + id 更新；列表/数量：前端发 GET，后端按 userId 与 read_at 查库返回，前端不再回写状态。 |
