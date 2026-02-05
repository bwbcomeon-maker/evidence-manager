# Phase 4 可执行计划与改动清单（收口 + 可验证 + 可交付）

> 基于《系统权限白皮书（V1）》与已完成的 P0～P3，本阶段目标：统一权限位输出、完善项目/成员流程、PMO 导入、审计只读体验、回归测试补齐。**先输出计划与清单，再按优先级实施。**

---

## 一、优先级与实施顺序

| 优先级 | 项 | 目标 | 依赖 |
|--------|----|------|------|
| **P1-1** | 统一权限位输出 | ProjectVO/EvidenceVO/EvidenceListItemVO 增加 permissions{5 位}，getProjectDetail/getEvidenceById/pageEvidence/listEvidences 全填充；前端按钮只读 permissions | 无 |
| **P1-2** | 项目分配 PM / 成员管理流程 | PMO 可在项目详情/成员页分配项目经理（owner）；项目经理可添加 editor/viewer；页面展示当前 PM 与成员角色 | P1-1（可选，现有 canManageMembers 已可用） |
| **P1-3** | PMO Excel 批量导入项目（最小版） | 模板、上传导入、批量 upsert、返回成功/失败明细 | 无 |
| **P2-1** | 审计入口只读体验 | 作废证据页展示作废原因/人/时间，明确“可查看不等于可操作” | 后端 VO 带作废信息 |
| **P2-2** | 回归测试补齐 | service/controller 测试覆盖 PMO/owner/editor/viewer；可选并发唯一 owner | 无 |

**建议实施顺序**：P1-1 → P1-2 → P1-3 → P2-1 → P2-2（P2-1 可与 P1-1 并行部分前端）。

---

## 二、P1-1 统一权限位输出 — 改动清单

### 2.1 评估：改动面与风险

- **改动面**：后端 4 处填充（getProjectDetail / getEvidenceById / listEvidences / pageEvidence）、1 个权限计算入口、2 个 VO + 1 个 DTO；前端 3～4 处按钮显隐。
- **风险**：listEvidences 当前仅传 userId，需增加 username/roleCode 才能按项目算权限；pageEvidence 已有 username/roleCode，可直接填充。**兼容**：保留现有 canInvalidate/canManageMembers 扁平字段，与 permissions 同时赋值，前端可逐步切到 permissions。

### 2.2 具体文件与方法

| 层级 | 文件 | 方法/改动 |
|------|------|-----------|
| DTO | `dto/PermissionBits.java`（新建） | 5 字段：canUpload, canSubmit, canArchive, canInvalidate, canManageMembers |
| DTO | `dto/ProjectVO.java` | 增加 `PermissionBits permissions`；保留 canInvalidate/canManageMembers（兼容） |
| DTO | `dto/EvidenceListItemVO.java` | 增加 `PermissionBits permissions`；保留 canInvalidate（兼容） |
| 工具 | `util/PermissionUtil.java` | 新增 `PermissionBits computeProjectPermissionBits(Long projectId, UUID userId, String roleCode)`：SYSTEM_ADMIN 全 true；AUDITOR 全 false；PMO 仅 canManageMembers=true，其余按 created_by/ACL owner/editor/viewer 算 |
| 服务 | `service/ProjectService.java` | `getProjectDetail`：调用 computeProjectPermissionBits，set 到 vo.permissions 并回填扁平 canInvalidate/canManageMembers；**可选** set canUpload/canSubmit/canArchive 到 vo（若 vo 保留扁平） |
| 服务 | `service/EvidenceService.java` | `getEvidenceById`：同项目权限计算，set vo.permissions + canInvalidate；`listEvidences`：签名增加 `String username, String roleCode`，对每条 item 用 (item.getProjectId(), userId, roleCode) 计算并 set permissions；`pageEvidence`：对每条 record 计算并 set permissions |
| 控制器 | `web/EvidenceController.java` | `listEvidences`：传入 user.getUsername(), user.getRoleCode() 给 service |
| 前端 | `api/projects.ts` | ProjectVO 类型增加 `permissions?: PermissionBits` |
| 前端 | `api/evidence.ts` | EvidenceListItem 增加 `permissions?: PermissionBits` |
| 前端 | `views/ProjectDetail.vue` | 上传按钮 `v-if="project?.permissions?.canUpload ?? project?.canUpload"`（兼容） |
| 前端 | `views/evidence/EvidenceDetail.vue` | canSubmit/canArchive/canVoid 优先用 `evidence?.permissions?.canSubmit/canArchive/canInvalidate`，兼容现有 canInvalidate |
| 前端 | `components/EvidenceList.vue`（若有列表内操作按钮） | 用 `item.permissions` 控制显隐 |

### 2.3 权限位计算规则（V1 复述）

- **SYSTEM_ADMIN**：5 位全 true。
- **AUDITOR**：5 位全 false。
- **PMO**：canManageMembers=true；canUpload/canSubmit/canArchive/canInvalidate 仅当该项目 created_by 或 ACL owner/editor 时为 true（owner 全 true，editor 仅 canUpload/canSubmit true）。
- **created_by / ACL owner**：5 位全 true。
- **ACL editor**：canUpload=true, canSubmit=true；canArchive=false, canInvalidate=false, canManageMembers=false。
- **ACL viewer**：5 位全 false。

---

## 三、P1-2 项目分配 PM / 成员管理 — 改动清单

### 3.1 评估

- 后端：addOrUpdateMember(role=owner) 已实现“删旧 owner + 增新 owner”，PMO 已可管理成员。**缺口**：项目详情/成员页需展示“当前项目经理”，便于 PMO 分配与替换。
- 改动面：ProjectVO 增加当前 PM 展示字段；getProjectDetail 查询 ACL owner 并解析 displayName；前端成员页/项目详情展示“当前项目经理”。

### 3.2 具体文件与方法

| 层级 | 文件 | 方法/改动 |
|------|------|-----------|
| DTO | `dto/ProjectVO.java` | 增加 `String currentPmUserId`（UUID 字符串）、`String currentPmDisplayName`（可选） |
| 服务 | `service/ProjectService.java` | `getProjectDetail`：查 auth_project_acl 中 role=owner 的一条，若无则用 project.getCreatedBy()；查 auth_user 取 displayName，set vo.currentPmUserId/currentPmDisplayName |
| 前端 | `api/projects.ts` | ProjectVO 增加 currentPmUserId?, currentPmDisplayName? |
| 前端 | `views/ProjectDetail.vue` | 详情区或成员入口旁展示“当前项目经理：{{ project.currentPmDisplayName || '未指定' }}” |
| 前端 | `views/ProjectMembers.vue` | 列表上方或标题下展示“当前项目经理：xxx”；添加成员时角色含 owner（分配 PM） |

---

## 四、P1-3 PMO Excel 批量导入项目 — 改动清单

### 4.1 评估

- 最小版：提供模板（code, name, description）、上传接口、解析 Excel、按 code upsert、返回成功/失败明细；不做批次表。
- 依赖：Apache POI（或已有依赖）；接口仅 SYSTEM_ADMIN/PMO 可调。

### 4.2 具体文件与方法

| 层级 | 文件 | 方法/改动 |
|------|------|-----------|
| DTO | `dto/ProjectImportResult.java`（新建） | total, successCount, failCount, details: List<{ row, code, success, message }> |
| 服务 | `service/ProjectService.java` | `importProjectsFromExcel(InputStream, UUID operatorUserId, String roleCode)`：校验 PMO/SYSTEM_ADMIN；解析每行 code/name/description；按 code 查存在则 update 否则 insert；收集每行结果 |
| 控制器 | `web/ProjectController.java` | `POST /api/projects/import`，MultipartFile，校验 PMO/SYSTEM_ADMIN，调用 importProjectsFromExcel，返回 ProjectImportResult |
| 前端 | 模板文件 | `public/项目导入模板.xlsx`（表头：项目令号、项目名称、项目描述） |
| 前端 | `api/projects.ts` | `importProjects(file: File)` |
| 前端 | `views/ProjectList.vue` 或独立页 | “批量导入”按钮（PMO 可见）、上传、展示导入结果明细 |

### 4.3 模板与解析约定

- 表头第 1 行：项目令号、项目名称、项目描述（或 code, name, description）。
- 空行跳过；code 为空则该行失败并记入 details。

---

## 五、P2-1 审计入口只读体验 — 改动清单

### 5.1 评估

- 作废证据页需展示：作废原因、作废人、作废时间；并再次强调“可查看不等于可操作”（VoidedEvidenceList 已有 NoticeBar，可加强文案）。
- 证据列表/详情 VO 需带 invalidReason、invalidBy、invalidAt（INVALID 时）；EvidenceItem 已有字段，EvidenceListItemVO 需增加并填充。

### 5.2 具体文件与方法

| 层级 | 文件 | 方法/改动 |
|------|------|-----------|
| DTO | `dto/EvidenceListItemVO.java` | 增加 invalidReason, invalidBy(UUID), invalidAt(OffsetDateTime)，作废证据展示用 |
| 服务 | `service/EvidenceService.java` | getEvidenceById/listEvidences/pageEvidence 组装 VO 时，从 EvidenceItem 拷贝 invalidReason/invalidBy/invalidAt |
| Mapper | `mapper/EvidenceItemMapper.xml`（或 select） | 确保 selectById/selectByProjectIdWithFilters/selectPageWithFilters 含 invalid_reason, invalid_by, invalid_at |
| 前端 | `api/evidence.ts` | EvidenceListItem 增加 invalidReason?, invalidBy?, invalidAt? |
| 前端 | `views/evidence/VoidedEvidenceList.vue` | 已有 NoticeBar；可加副文案“列表仅展示作废原因/人/时间，不可操作” |
| 前端 | 证据列表/详情（作废状态） | 展示作废原因、作废人、作废时间（列表可摘要，详情可完整） |

---

## 六、P2-2 回归测试补齐 — 改动清单

| 测试类/方法 | 覆盖 |
|-------------|------|
| `PermissionUtilTest`（已有） | checkCanArchive editor/viewer 403；admin/created_by/owner 通过 |
| `ProjectServiceTest`（新建或扩） | getProjectDetail：PMO 无项目身份时 canInvalidate=false、canManageMembers=true；owner 时 5 位符合预期 |
| `EvidenceServiceTest`（新建或扩） | getEvidenceById/listEvidences 返回的 permissions 与角色一致；archiveEvidence 非责任人 403 |
| `ProjectControllerTest` / `EvidenceVersionControllerTest` | 可选：PMO 调 getProjectDetail 返回 permissions；editor 调 archive 403 |
| 可选 | 并发：多线程同时 addOrUpdateMember(role=owner) 同一项目，断言最终仅 1 个 owner |

---

## 七、实施顺序与可交付物

1. **P1-1**：PermissionBits DTO → PermissionUtil.computeProjectPermissionBits → ProjectVO/EvidenceListItemVO 增加 permissions → getProjectDetail/getEvidenceById/listEvidences/pageEvidence 填充 → 前端按钮切 permissions。**交付**：接口返回统一 permissions；前端上传/提交/归档/作废/成员入口均依 permissions。
2. **P1-2**：ProjectVO 当前 PM 字段 → getProjectDetail 填充 → 前端项目详情/成员页展示当前 PM。**交付**：PMO/负责人可见“当前项目经理”，分配 PM 即添加成员选 owner。
3. **P1-3**：模板 + 导入接口 + 前端上传与结果展示。**交付**：PMO 可下载模板、上传 Excel、看到成功/失败明细。
4. **P2-1**：EvidenceListItemVO 作废三字段 → 三处组装填充 → 前端作废证据页/详情展示原因/人/时间 + 文案。**交付**：AUDITOR 只读体验完整。
5. **P2-2**：补充测试类与用例。**交付**：关键路径可回归、可选并发测试。

---

## 八、风险点与规避

- **listEvidences 签名变更**：EvidenceController 需传 username/roleCode，调用方仅此一处，风险低。
- **Excel 依赖**：若项目无 POI，需在 pom.xml 增加 `org.apache.poi:poi-ooxml`；注意大文件行数限制（如最多 500 行）。
- **前端兼容**：permissions 与扁平 canInvalidate/canManageMembers 同时存在，前端用 `permissions?.canX ?? canX` 可平滑过渡。

以上为 Phase 4 可执行计划与改动清单，下文按 P1-1 → P1-2 → P1-3 → P2-1 → P2-2 逐项实施。
