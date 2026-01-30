# 证据管理模块联调自测清单（按用户角色）

自测范围：EvidenceHome 入口可见性、我上传的证据、最近上传、作废证据、按文件类型、详情页预览/下载/作废、返回与导航。

---

## 一、角色与最小验证点（每角色至少 3 条）

### 1. SYSTEM_ADMIN

| 序号 | 验证点 | 预期 |
|------|--------|------|
| 1 | EvidenceHome 入口可见性 | 5 个入口均可见（含「作废证据」） |
| 2 | 我上传的证据 | 仅显示当前 admin 用户上传的证据 |
| 3 | 作废证据入口与访问 | 可进入 /evidence/voided，列表仅含 status=invalid 的证据，状态显示「作废」 |
| 4 | 详情页作废按钮 | 详情页展示「作废」按钮（按权限显示） |
| 5 | 返回与导航 | 从任意证据二级页返回可回到 /evidence |

### 2. PROJECT_OWNER

| 序号 | 验证点 | 预期 |
|------|--------|------|
| 1 | EvidenceHome 入口可见性 | 5 个入口均可见（含「作废证据」） |
| 2 | 我上传的证据 | 仅显示当前 PROJECT_OWNER 用户上传的证据 |
| 3 | 作废证据入口与访问 | 可进入 /evidence/voided，仅能看到其可见项目内的作废证据 |
| 4 | 按文件类型 | image/document/video 分类正确，仅可见项目内证据 |
| 5 | 返回与导航 | 二级页返回至 /evidence |

### 3. PROJECT_EDITOR

| 序号 | 验证点 | 预期 |
|------|--------|------|
| 1 | EvidenceHome 入口可见性 | 仅 4 个入口可见，「作废证据」入口不显示 |
| 2 | 直接访问 /evidence/voided | 提示「无权限访问」并跳回 /evidence |
| 3 | 我上传的证据 | 仅显示当前 PROJECT_EDITOR 用户上传的证据 |
| 4 | 最近上传的证据 | recentDays=7 / 30 切换生效，仅可见项目内 |
| 5 | 详情页作废按钮 | 不展示「作废」按钮（非 SYSTEM_ADMIN/PROJECT_OWNER/PROJECT_AUDITOR） |

### 4. PROJECT_VIEWER

| 序号 | 验证点 | 预期 |
|------|--------|------|
| 1 | EvidenceHome 入口可见性 | 仅 4 个入口可见，「作废证据」入口不显示 |
| 2 | 直接访问 /evidence/voided | 提示「无权限访问」并跳回 /evidence |
| 3 | 我上传的证据 | 仅显示当前用户上传的；若未上传则空列表 |
| 4 | 详情页预览/下载 | 预览、下载正常；不展示「作废」按钮 |
| 5 | 返回与导航 | 二级页返回至 /evidence |

### 5. PROJECT_AUDITOR

| 序号 | 验证点 | 预期 |
|------|--------|------|
| 1 | EvidenceHome 入口可见性 | 5 个入口均可见（含「作废证据」） |
| 2 | 作废证据入口与访问 | 可进入 /evidence/voided，仅授权项目内作废证据 |
| 3 | 默认列表不出现作废 | 「我上传」「最近上传」「按文件类型」默认不包含 status=invalid 的证据 |
| 4 | 详情页作废按钮 | 展示「作废」按钮（与 SYSTEM_ADMIN/PROJECT_OWNER 一致） |
| 5 | 按文件类型 | image/document/video 分类与 content_type 映射正确 |

---

## 二、通用自测项（所有角色）

| 类别 | 验证点 | 预期 |
|------|--------|------|
| 最近上传 | recentDays=7 | 仅显示 7 天内上传的证据 |
| 最近上传 | recentDays=30 | 仅显示 30 天内上传的证据 |
| 作废证据 | 默认列表 | 「我上传」「最近上传」「按文件类型」列表不包含作废证据 |
| 作废证据 | 状态标识 | 作废列表中每条显示「作废」标签（红色/明显） |
| 按文件类型 | image | 仅 content_type 为 image/* 的证据 |
| 按文件类型 | document | 仅 pdf/doc/docx/xls/xlsx/ppt/pptx/txt/zip/rar 等 |
| 按文件类型 | video | 仅 content_type 为 video/* 的证据 |
| 详情页 | 预览 | PDF/图片/文本可新窗口预览；不支持类型提示下载 |
| 详情页 | 下载 | 下载文件名与类型正确 |
| 返回 | 有 history | 左上角返回执行 back |
| 返回 | 无 history | 返回至 /evidence（replace） |

---

## 三、后端需准备的最小假数据

- **用户（与 Session 一致）**：`sys_user` 中至少 5 个账号，`role_code` 分别为 SYSTEM_ADMIN、PROJECT_OWNER、PROJECT_EDITOR、PROJECT_VIEWER、PROJECT_AUDITOR；密码统一便于联调（如 `Test@12345`）。
- **用户映射**：`auth_user` 中与上述 5 个账号 **username 一一对应** 的 5 条记录（用于「我上传的证据」和可见项目解析）。
- **项目**：至少 **2 个项目**，`created_by` 为某 auth_user 的 UUID（建议 PROJECT_OWNER 对应 uuid），保证不同角色通过 project 或 auth_project_acl 能「可见」到至少 1 个项目。
- **证据**：  
  - 至少 **2 个项目下各有证据**，且每个项目内覆盖 **3 类文件**：  
    - **image**：如 `content_type = 'image/png'` 或 `image/jpeg`；  
    - **document**：如 `content_type = 'application/pdf'`；  
    - **video**：如 `content_type = 'video/mp4'`。  
  - 至少 **1 条证据** `status = 'invalid'`（作废），其余为 `active`。  
  - `evidence_item.created_by`、`evidence_version.uploader_id` 使用 auth_user 的 UUID，且部分证据由不同测试用户上传，便于验证「我上传的证据」。
- **项目权限**：`auth_project_acl` 中为 PROJECT_EDITOR / PROJECT_VIEWER / PROJECT_AUDITOR 分配至少 1 个项目的相应权限，保证他们能看见该项目下的证据列表与详情。

**具体 SQL**：见 `backend/app/init-evidence-test-data.sql`。  
**执行顺序**：先跑完 Flyway 迁移（V1～V4）及 `init-dev-data.sql`（可选），再执行 `init-evidence-test-data.sql`。  
**账号**：admin 密码见 V4（如 `Admin@12345`）；owner1/editor1/viewer1/auditor1 密码均为 `Test@12345`。  
**说明**：首次执行前请确保 `auth_user` 中不存在 admin/owner1/editor1/viewer1/auditor1（否则与脚本中固定 UUID 不一致会影响「我上传的证据」）；重复执行会因 ON CONFLICT 跳过。

---

## 四、前端需准备的最小假数据

- 前端 **不维护证据业务假数据**，依赖后端接口与上述假数据。
- 可选：在开发环境配置 **多个测试账号**（与后端 sys_user 一致），用于切换角色做入口显隐与访问拦截自测；或使用浏览器无痕/多账号登录。

---

## 五、快速自测顺序建议

1. 使用 **SYSTEM_ADMIN** 登录 → 检查 5 个入口、作废列表、详情作废按钮、预览/下载、返回。
2. 使用 **PROJECT_EDITOR** 或 **PROJECT_VIEWER** 登录 → 检查仅 4 个入口、直接访问 /evidence/voided 被拦截、详情无作废按钮。
3. 使用 **PROJECT_OWNER** / **PROJECT_AUDITOR** 登录 → 检查含「作废证据」入口且可访问 /evidence/voided。
4. 切换 **最近上传** 7/30 天、**按文件类型** image/document/video，核对列表与 content_type 一致。
5. 用不同账号验证「我上传的证据」仅包含当前用户上传。
