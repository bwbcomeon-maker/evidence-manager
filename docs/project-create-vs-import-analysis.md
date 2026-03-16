# 创建单项目 vs 批量导入：业务字段、逻辑与差异梳理

本文档仅做梳理与问题发现，不修改代码。

---

## 一、项目（Project）相关业务字段与数据模型

### 1.1 表 `project` 字段（与业务强相关）

| 字段 | 类型 | 说明 | 创建单项目 | 批量导入（新增） | 批量导入（更新） |
|------|------|------|------------|------------------|------------------|
| id | BIGINT | 主键，自增 | 插入后生成 | 插入后生成 | 不变 |
| code | VARCHAR | 项目令号，唯一 | 必填，前端/API 传入，trim 后写入 | 第 1 列，必填，为空则报错跳过 | 不更新 |
| name | VARCHAR | 项目名称 | 必填，trim，空抛错 | 第 2 列，空则 "" | 更新为 Excel 第 2 列 |
| description | VARCHAR/TEXT | 项目描述 | 可选，空转 null | 第 3 列，空则 "" | 更新为 Excel 第 3 列 |
| status | VARCHAR | 项目状态 | 固定 `active` | 固定 `active` | 不更新 |
| has_procurement | BOOLEAN | 是否含采购（影响 S1 阶段「项目前期产品比测报告」是否必填） | 必填，默认 true（前端） | **未设置，DB 默认 false** | **不更新** |
| created_by_user_id | BIGINT | 创建人 | 当前用户 id | 操作人（导入者）id | 不更新 |
| created_at | TIMESTAMPTZ | 创建时间 | DB CURRENT_TIMESTAMP | DB CURRENT_TIMESTAMP | 不更新 |
| updated_at | TIMESTAMPTZ | 更新时间 | DB CURRENT_TIMESTAMP | 更新时 CURRENT_TIMESTAMP | 更新时 CURRENT_TIMESTAMP |

### 1.2 关联表（与“创建项目”强相关）

- **auth_project_acl**  
  - 含义：项目成员与角色（owner/editor/viewer）。  
  - **创建单项目**：插入一条 `owner`，`sys_user_id = 当前用户`，创建人即项目负责人。  
  - **批量导入**：**不插入**任何 ACL，注释写明「批量导入的项目不自动添加操作人为成员，成员需后续在成员管理中分配」。

- **project_stage**  
  - 含义：项目在各交付阶段上的进度（NOT_STARTED / IN_PROGRESS / COMPLETED）。  
  - **创建单项目**：不在此处创建；由 **StageProgressService.ensureProjectStages(projectId)** 在首次加载阶段进度时懒初始化（根据 `delivery_stage` 表插入 5 条 project_stage）。  
  - **批量导入**：同样不在此处创建；首次打开项目证据/阶段进度时同样走 `ensureProjectStages`，行为一致。

- **stage_evidence_template**（只读配置）  
  - 阶段证据模板，决定每个阶段要交哪些证据；其中 `required_when = 'HAS_PROCUREMENT'` 的项依赖 `project.has_procurement`。  
  - 与“创建/导入”无直接写关系，但 **has_procurement** 的差异会直接影响导入项目的阶段必填项计算。

---

## 二、创建单个项目的业务逻辑与实现

### 2.1 入口与权限

- **接口**：`POST /api/projects`  
- **权限**：仅 `SYSTEM_ADMIN` 或 `PMO`，否则 403。  
- **请求体**：`CreateProjectRequest`  
  - `code`：项目令号，必填，校验 `@NotBlank`  
  - `name`：项目名称，必填，校验 `@NotBlank`  
  - `description`：可选  
  - `hasProcurement`：可选，前端默认 true  

### 2.2 后端核心逻辑（ProjectService.createProject）

1. **校验**  
   - `code` trim 后为空 → 400「项目令号不能为空」。  
   - `projectMapper.selectByCode(code)` 已存在 → 400「项目令号已存在」。

2. **写 project**  
   - `code` = trim(code)  
   - `name` = trim(name)，若为 null 则 ""  
   - `description` = trim(description)，空则 null  
   - `hasProcurement` = `Boolean.TRUE.equals(hasProcurement)`（未传则 false，前端一般传 true）  
   - `status` = `active`  
   - `createdByUserId` = 当前用户 id  
   - insert 后由 DB 生成 id、created_at、updated_at（Mapper 中 `COALESCE(#{hasProcurement}, false)` 等）。

3. **写 auth_project_acl**  
   - 插入一条：`project_id` = 新项目 id，`sys_user_id` = 当前用户，`role` = `owner`。  
   - 即：创建人自动成为该项目负责人（owner）。

4. **返回值**  
   - `toVO(project)` 返回项目基本信息（含 id、code、name、description、status、hasProcurement、createdAt 等），无 project_stage 的创建。

### 2.3 前端（ProjectList.vue 新建项目）

- 表单字段：项目令号、项目名称、项目描述、**是否含采购**（createForm.hasProcurement，默认 true）。  
- 提交：`createProject({ code, name, description, hasProcurement })`。  
- 成功后跳转项目详情 `/projects/${data.id}`；首次进入证据 Tab 时会请求阶段进度，触发后端 `ensureProjectStages`，从而创建 project_stage。

### 2.4 小结：单项目创建涉及的业务字段与关联

- **直接写入**：`project`（code, name, description, has_procurement, status, created_by_user_id）、`auth_project_acl`（一条 owner）。  
- **懒创建**：`project_stage` 在首次查阶段进度时按 `delivery_stage` 初始化。  
- **不创建**：证据、归档申请等，均与“创建项目”解耦。

---

## 三、批量导入项目的业务逻辑与实现

### 3.1 入口与权限

- **接口**：`POST /api/projects/import`，multipart，字段名 `file`。  
- **权限**：仅 `SYSTEM_ADMIN` 或 `PMO`，否则 403。  
- **模板**：`GET /api/projects/import/template` 下载 xlsx，表头 3 列：**项目令号、项目名称、项目描述**（无「是否含采购」列）。

### 3.2 后端核心逻辑（ProjectService.importProjectsFromExcel）

- **表头**：第 0 行视为表头，从第 1 行起读数据。  
- **最大行数**：只处理前 500 行（`IMPORT_MAX_ROWS`）。  
- **按行**：  
  - 第 0 列 = code（项目令号），第 1 列 = name（项目名称），第 2 列 = description（项目描述）。  
  - code 为空或空白 → 记 RowError「项目令号为空」，continue。  
  - code/name/description 均 trim；name/description 空则置为 ""。

**对每一行：**

1. **按 code 查已有项目**  
   - `projectMapper.selectByCode(code)`。

2. **若已存在（update 分支）**  
   - 比较 `name`、`description` 与库中是否一致（已 trim）。  
   - 若完全一致 → **skipped++**，不写库。  
   - 若不一致 → 只更新 `existing.setName(nameVal)`、`existing.setDescription(descVal)`、`existing.setUpdatedAt(now)`，**不更新** `code`、`status`、**hasProcurement**、created_by_user_id 等。  
   - `projectMapper.update(existing)`，updated++。

3. **若不存在（insert 分支）**  
   - `Project project = new Project()`  
   - 设置：`code`、`name`、`description`、`status = active`、`createdByUserId = operatorUserId`。  
   - **未设置 hasProcurement**（Java 为 null，Mapper 中 `COALESCE(#{hasProcurement}, false)` → 库中为 **false**）。  
   - `projectMapper.insert(project)`，inserted++。  
   - **不插入** auth_project_acl，即导入的新项目没有任何成员。

4. **异常**  
   - 单行异常捕获后记入 `errors`（rowNum, code, message），不中断整体导入。

5. **返回值**  
   - `ProjectImportResult`：total、inserted、updated、skipped、errors。

### 3.3 前端（ProjectList.vue 批量导入）

- 选择 xlsx → 调用 `importProjects(file)`，展示 inserted/updated/skipped/errors。  
- 模板说明文案：「模板列：项目令号、项目名称、项目描述。」与后端一致，无 hasProcurement。

### 3.4 小结：批量导入涉及的业务字段与关联

- **直接写入**：仅 `project` 表；新增时只写 code、name、description、status、created_by_user_id，**不写 has_procurement**（依赖 DB 默认 false）；更新时只写 name、description、updated_at。  
- **不写入**：auth_project_acl（导入的新项目无成员）。  
- **懒创建**：project_stage 仍与单项目一致，在首次加载阶段进度时由 `ensureProjectStages` 创建。

---

## 四、创建单项目 vs 批量导入：差异与问题

### 4.1 字段级差异

| 项目 | 创建单项目 | 批量导入（新增） | 批量导入（更新） |
|------|------------|------------------|------------------|
| code | 必填，唯一 | 必填，唯一 | 不更新 |
| name | 必填 | 可为空字符串 | 更新 |
| description | 可选 | 可为空字符串 | 更新 |
| status | 固定 active | 固定 active | 不更新 |
| **has_procurement** | **显式传入，前端默认 true** | **未设置 → DB 默认 false** | **不更新（保持原值）** |
| created_by_user_id | 当前用户 | 导入操作人 | 不更新 |
| auth_project_acl | **插入一条 owner（当前用户）** | **不插入** | 不涉及 |

### 4.2 业务逻辑差异

1. **是否含采购（has_procurement）**  
   - 单项目：由用户在表单选择，通常为 true，并参与阶段证据必填规则（如 S1 的「项目前期产品比测报告」在 has_procurement=true 时必填）。  
   - 导入新增：永远为 false，与单项目默认不一致；若业务上多数项目“含采购”，则导入项目会少一条必填项，或与产品预期不符。  
   - 导入更新：不更新 has_procurement，无法通过 Excel 修改该字段。

2. **项目成员（auth_project_acl）**  
   - 单项目：创建人自动为 owner，立即可见、可管理该项目。  
   - 导入新增：无任何成员，需 PMO/管理员事后在「成员管理」或「批量分配项目」中分配，否则普通用户看不到该项目。

3. **唯一性与更新策略**  
   - 单项目：code 唯一，重复则 400。  
   - 导入：按 code 做 upsert；存在则仅更新 name/description（且完全一致则跳过），不更新 code/status/hasProcurement；不存在则新增。  
   - 导入不校验 name 是否为空，新增时 name 可为 ""，与单项目“项目名称不能为空”不一致。

### 4.3 发现的问题（仅梳理，不改代码）

1. **has_procurement 不一致**  
   - 导入新增项目恒为 false，单项目默认 true，导致阶段必填项规则不一致。  
   - 若希望与单项目一致，需在导入逻辑中显式设置 hasProcurement（或从 Excel 增加一列并在导入时解析）。

2. **导入新增项目无成员**  
   - 设计上为“由管理员后续分配”，但若未分配，项目对普通用户不可见；与单项目“创建即可见”的体验不同，需在说明或流程上区分。

3. **导入模板无「是否含采购」**  
   - 模板仅 3 列，无法通过 Excel 指定 has_procurement；若业务希望批量导入时也能区分是否含采购，需扩展模板与解析逻辑。

4. **导入时 name 可为空**  
   - 当前导入对 name 空值仅转为 ""，不报错；单项目则“项目名称不能为空”。若希望一致，导入时应对 name 做非空校验（或与单项目统一规则）。

5. **更新时字段不全**  
   - 导入更新仅覆盖 name、description，不覆盖 has_procurement、status 等；若未来需要通过 Excel 批量修正“是否含采购”，当前实现不支持。

---

## 五、关联关系简表（与创建/导入相关）

- **project**  
  - 1 : N **auth_project_acl**（项目成员；单项目创建时写 1 条 owner，导入不写）。  
  - 1 : N **project_stage**（阶段进度；创建与导入均不直接写，由 StageProgressService 懒初始化）。  
  - 1 : N **evidence_item**（证据项；创建/导入均不创建）。  
  - 1 : N **project_archive_application**（归档申请；创建/导入均不创建）。  

- **project_stage** 依赖 **delivery_stage**（阶段主数据），`ensureProjectStages` 按 delivery_stage 为项目生成 5 条 project_stage。  

- **stage_evidence_template** 中 `required_when = 'HAS_PROCUREMENT'` 的模板项是否参与必填，由 **project.has_procurement** 决定，因此 has_procurement 的差异会直接影响导入项目的阶段完成度与必填项展示。

---

以上为创建单项目与批量导入所涉及的业务字段、实现逻辑及与项目相关的关联关系梳理，以及二者差异与发现的问题汇总。
