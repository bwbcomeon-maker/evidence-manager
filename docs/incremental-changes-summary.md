# 增量增强改造说明（作废权限 + PMO + 审计）

## 一、改动文件清单

| 序号 | 文件路径 | 改动内容 |
|-----|----------|----------|
| 1 | `backend/app/src/main/resources/mapper/EvidenceItemMapper.xml` | `updateEvidenceInvalidate`：WHERE 改为 `id=? AND evidence_status<>'INVALID'`，去掉对 expectedCurrentStatus 的依赖；注释明确写入 invalid_at（优先） |
| 2 | `backend/app/src/main/java/com/bwbcomeon/evidence/mapper/EvidenceItemMapper.java` | `updateEvidenceInvalidate` 去掉参数 `expectedCurrentStatus` |
| 3 | `backend/app/src/main/java/com/bwbcomeon/evidence/service/EvidenceService.java` | 作废：调用 `updateEvidenceInvalidate` 不再传 expectedCurrentStatus；影响行数=0 时抛出「已作废或状态不允许」；`buildEvidenceSnapshotJson` 快照增加 `status`、`invalid_reason`、`invalid_by`、`invalid_at` 键名，满足审计要求 |
| 4 | `backend/app/src/main/resources/mapper/AuditLogMapper.xml` | insert 中 `before_data`、`after_data` 使用 `cast(#{beforeData} as jsonb)`、`cast(#{afterData} as jsonb)`，String 入参写入 JSONB，避免 TypeHandler |
| 5 | `backend/app/src/main/resources/db/migration/V8__audit_log_add_project_and_snapshots.sql` | 明确 `project_id`/`before_data`/`after_data` 为 NULL 可空；注释说明 project_id 与 project.id(BIGINT) 一致 |

**未改动的现状确认：**

- **evidence_item 表**：已存在 `evidence_status`、`invalid_reason`、`invalid_by`、`invalid_at`、`invalid_time`（V1+V5），未新增字段；作废时同时写 `invalid_at` 与 `invalid_time`，业务优先使用 `invalid_at`。
- **PermissionUtil.checkCanInvalidate**：已存在，逻辑为 SYSTEM_ADMIN 放行、project.created_by==userId 放行、auth_project_acl(project_id, user_id=UUID, role='owner') 放行，无需修改。
- **Controller**：已接收 `{invalidReason}`、传 roleCode、成功后写审计。
- **ProjectVO/EvidenceListItemVO.canInvalidate**：已在 getProjectDetail/getEvidenceById 中按与 checkCanInvalidate 一致逻辑计算，单条查询无 N+1。
- **PMO**：`AdminUserService.VALID_ROLE_CODES` 已含 PMO；`getVisibleProjectIds` 在 roleCode=PMO 时与 SYSTEM_ADMIN 一致。
- **project.id 类型**：为 BIGINT（V1 project 表）；`audit_log.project_id` 为 BIGINT 可空，类型一致。

---

## 二、为何不破坏现有功能（兼容点）

| 改动项 | 兼容点 |
|--------|--------|
| updateEvidenceInvalidate WHERE 改为 `evidence_status<>'INVALID'` | 仅限制「已作废」不能再次更新，未作废证据行为与原先一致；去掉 expectedCurrentStatus 后仍能防止重复作废与并发误更新。 |
| 0 行返回「已作废或状态不允许」 | 仅错误文案更明确，接口契约与 HTTP 状态码不变。 |
| 审计快照增加 status/invalid_reason/invalid_by/invalid_at | 仅增加 JSON 键，不改变现有调用方；旧审计记录无 before_data/after_data 仍为 null。 |
| Audit insert 使用 cast(#{beforeData} as jsonb) | 旧调用不传 beforeData/afterData 时传 null，cast(null as jsonb) 在 PostgreSQL 中合法，列为可空，不影响现有 insert。 |
| V8 列显式 NULL 可空 | ADD COLUMN 默认即可空，显式写出 NULL 仅文档化，不改变行为。 |
| 不新增角色表、不改用户主键、不改 ACL 枚举/主键 | 满足「禁止」约束，无迁移与兼容风险。 |

---

## 三、关键路径手工测试步骤

### 1. 能作废 + 原因必填 + 审计写入成功

- **前置**：存在项目 P（created_by=当前用户 或 当前用户在该项目 ACL 中为 owner）或当前用户为 SYSTEM_ADMIN；存在证据 E，evidence_status 为 DRAFT 或 SUBMITTED。
- **步骤**：
  1. 登录具有该项目「项目责任人」权限的账号（项目创建人或 ACL owner 或 SYSTEM_ADMIN）。
  2. 打开项目详情或证据详情，对证据 E 点击「作废」。
  3. 在弹窗中**不填**作废原因点确定 → 应提示「请填写作废原因」或接口返回 400「作废原因不能为空」。
  4. 填写作废原因（如「误传」）后确定 → 应提示作废成功。
  5. 再次对同一证据 E 点击作废并填原因 → 应提示「已作废或状态不允许」或接口返回 400 对应文案。
  6. 在 `audit_log` 表中查询 action='EVIDENCE_INVALIDATE' 的最新一条：应存在 project_id=项目ID、target_type='EVIDENCE'、target_id=证据ID、before_data/after_data 为 JSONB，且 after_data 中含 status、invalid_reason、invalid_by、invalid_at。

### 2. 不能作废（无权限）

- **前置**：存在项目 P 和证据 E；当前用户非项目创建人、非该项目 ACL 中的 owner、且非 SYSTEM_ADMIN（例如仅为 PROJECT_VIEWER 或 PROJECT_EDITOR）。
- **步骤**：
  1. 登录上述无「项目责任人」权限的账号。
  2. 进入项目详情或证据详情（若有可见权限）：作废按钮应**不展示**（前端依 canInvalidate 隐藏）。
  3. 若通过接口直接调用 `POST /api/evidence/{id}/invalidate` 且 body 带 `{ "invalidReason": "测试" }` → 应返回 **403**，文案为「仅项目责任人可作废证据」。

---

## 四、数据库与业务现状确认（检查结果）

- **evidence_item**：evidence_status、invalid_reason、invalid_by、invalid_at、invalid_time 均已存在；作废时写入 invalid_at（并同步 invalid_time 与 evidence_status）。
- **project.id**：BIGINT；audit_log.project_id：BIGINT 可空，类型一致。
- **PermissionUtil.checkCanInvalidate**：逻辑符合 SYSTEM_ADMIN / created_by / ACL owner 三项放行。
- **PMO**：已加入 VALID_ROLE_CODES，getVisibleProjectIds 已对 PMO 与 SYSTEM_ADMIN 同等处理。
