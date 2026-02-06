# 项目成员批量分配

PMO 批量导入大量项目后，可通过以下两种方式批量维护成员，无需逐个项目、逐个成员操作。

---

## 1. 批量对一个人分配多个项目

**场景**：将同一人（如项目经理或成员）一次性加入多个项目，并指定角色。

- **接口**：`POST /api/projects/batch-members`
- **权限**：仅 **PMO** 或 **系统管理员**（因可管理任意项目）。
- **请求体**：
  ```json
  {
    "userId": 2,
    "projectIds": [1, 2, 3, 10, 20],
    "role": "editor"
  }
  ```
  - `userId`：被分配用户 `sys_user.id`（必填）
  - `projectIds`：项目 id 列表（必填，非空）
  - `role`：`owner`（项目经理）/ `editor` / `viewer`，默认 `editor`

- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "successCount": 4,
      "failCount": 1,
      "errors": ["项目10: 项目不存在"]
    }
  }
  ```
  按项目逐个执行，单项目失败不影响其他项目；`errors` 为失败项原因。

- **说明**：
  - 若某项目已存在该用户，则更新其角色为本次传入的 `role`。
  - 若指定 `role: "owner"`，每个项目会设该用户为项目经理（每项目仅一个 owner，会先移除原 owner）。

---

## 2. 对一个项目批量分配多名成员（含项目经理）

**场景**：为某一个项目一次性添加/调整多人，包括指定项目经理（owner）、编辑（editor）、查看（viewer）。

- **接口**：`POST /api/projects/{projectId}/members/batch`
- **权限**：对该项目有「管理成员」权限的用户（项目创建人、该项目 ACL owner、或 SYSTEM_ADMIN/PMO）。
- **请求体**：
  ```json
  {
    "members": [
      { "userId": 3, "role": "owner" },
      { "userId": 4, "role": "editor" },
      { "userId": 5, "role": "viewer" }
    ]
  }
  ```
  - `members`：成员列表，每项 `userId`（必填）+ `role`（owner/editor/viewer）

- **响应**：同 1，`BatchAssignResult`（successCount、failCount、errors）。

- **说明**：
  - 若列表中包含 `role: "owner"`，该用户会被设为该项目项目经理（每项目唯一 owner，会先移除原 owner）。
  - 单条失败（如用户不存在、不能修改自己）仅记录到 `errors`，不影响其他成员写入。

---

## 前端调用示例

```ts
import {
  batchAssignUserToProjects,
  batchAddProjectMembers,
  type BatchAssignResult,
  type AddProjectMemberBody
} from '@/api/projects'

// 将用户 2 批量加入项目 1、2、3，角色为编辑
const res = await batchAssignUserToProjects({
  userId: 2,
  projectIds: [1, 2, 3],
  role: 'editor'
})
if (res.data?.successCount) {
  console.log(`成功 ${res.data.successCount} 项`)
  if (res.data.errors?.length) console.warn(res.data.errors)
}

// 为项目 1 批量添加成员：项目经理 + 两名编辑
const res2 = await batchAddProjectMembers(1, {
  members: [
    { userId: 3, role: 'owner' },
    { userId: 4, role: 'editor' },
    { userId: 5, role: 'editor' }
  ]
})
```

---

## 典型流程（PMO 批量上传项目后）

1. **批量导入项目**：PMO 使用「项目导入」上传 Excel，生成大量项目。
2. **批量指定项目经理**：  
   - 方式 A：对一个人分配多项目并设 owner：`batchAssignUserToProjects({ userId: 张三, projectIds: [所有需其负责的项目 id], role: 'owner' })`。  
   - 方式 B：对每个项目单独批量加人：进入项目详情 → 成员管理 → 调用 `batchAddProjectMembers(projectId, { members: [{ userId: 张三, role: 'owner' }, ...] })`（若前端提供批量录入界面）。
3. **批量指定成员**：对同一人 `batchAssignUserToProjects({ userId, projectIds, role: 'editor' })` 或 `role: 'viewer'`，或按项目用 `batchAddProjectMembers` 一次添加多人。

页面入口可由「用户管理」或「项目列表」提供「批量分配项目」按钮（选用户 + 选项目 + 选角色），调用 `batchAssignUserToProjects`；项目详情成员管理页提供「批量添加成员」表单，调用 `batchAddProjectMembers`。
