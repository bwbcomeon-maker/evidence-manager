import http from './http'

/** V1 统一权限位（与后端同源，前端只读） */
export interface PermissionBits {
  canUpload?: boolean
  canSubmit?: boolean
  canArchive?: boolean
  canInvalidate?: boolean
  canManageMembers?: boolean
}

export interface ProjectVO {
  id: number
  code: string
  name: string
  description: string
  /** 项目状态：active | pending_approval | returned | archived | voided */
  status: string
  createdAt?: string
  /** 项目创建人 sys_user.id */
  createdByUserId?: number
  /** 项目创建人展示名 */
  createdByDisplayName?: string
  /** V1 统一权限位 */
  permissions?: PermissionBits
  canInvalidate?: boolean
  canManageMembers?: boolean
  canUpload?: boolean
  /** 当前项目经理 sys_user.id */
  currentPmUserId?: number
  /** 当前项目经理展示名 */
  currentPmDisplayName?: string
  /** 证据完成度 0–100（stage-progress 事实源，列表扩展） */
  evidenceCompletionPercent?: number
  /** 关键缺失摘要，前若干条（列表扩展） */
  keyMissingSummary?: string[]
  /** 是否含采购（影响项目启动阶段「项目前期产品比测报告」是否必填） */
  hasProcurement?: boolean | null
}

export interface ProjectMemberVO {
  userId: number
  role: string
  username?: string
  displayName?: string
  /** 是否为当前登录用户（当前用户不显示编辑/移除） */
  isCurrentUser?: boolean
}

export interface AddProjectMemberBody {
  userId: number
  role: 'owner' | 'editor' | 'viewer'
}

/** 项目成员列表 */
export const getProjectMembers = (projectId: number) =>
  http.get<ApiResult<ProjectMemberVO[]>>(`/projects/${projectId}/members`)

/** 添加或调整项目成员 */
export const addOrUpdateProjectMember = (projectId: number, body: AddProjectMemberBody) =>
  http.post<ApiResult<unknown>>(`/projects/${projectId}/members`, body)

/** 移除项目成员 */
export const removeProjectMember = (projectId: number, userId: number) =>
  http.delete<ApiResult<unknown>>(`/projects/${projectId}/members/${userId}`)

/** 批量分配结果（成功数、失败数、失败原因列表） */
export interface BatchAssignResult {
  successCount: number
  failCount: number
  errors: string[]
}

/** 批量将一人分配至多个项目（仅 PMO/系统管理员）body: { userId, projectIds, role? } */
export const batchAssignUserToProjects = (body: {
  userId: number
  projectIds: number[]
  role?: 'owner' | 'editor' | 'viewer'
}) =>
  http.post<ApiResult<BatchAssignResult>>('/projects/batch-members', body)

/** 批量为一个项目添加多名成员（含项目经理 owner）body: { members: [{ userId, role }, ...] } */
export const batchAddProjectMembers = (projectId: number, body: { members: AddProjectMemberBody[] }) =>
  http.post<ApiResult<BatchAssignResult>>(`/projects/${projectId}/members/batch`, body)

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface CreateProjectBody {
  code: string
  name: string
  description?: string
  /** 是否含采购（项目启动阶段「项目前期产品比测报告」勾选后为必填） */
  hasProcurement?: boolean
}

/** 项目列表（当前用户可见） */
export const getProjects = () =>
  http.get<ApiResult<ProjectVO[]>>('/projects')

/** 项目详情（不可见返回 403） */
export const getProjectDetail = (id: number) =>
  http.get<ApiResult<ProjectVO>>(`/projects/${id}`)

/** 更新项目基本信息（名称/描述/是否含采购），需具备管理成员权限 */
export const updateProject = (
  projectId: number,
  body: { name?: string; description?: string; hasProcurement?: boolean }
) =>
  http.patch<ApiResult<ProjectVO>>(`/projects/${projectId}`, body)

export const createProject = (body: CreateProjectBody) =>
  http.post<ApiResult<ProjectVO>>('/projects', body)

/** 作废项目（仅进行中且未产生业务数据） */
export const voidProject = (projectId: number) =>
  http.post<ApiResult<null>>(`/projects/${projectId}/void`)

// ---------- 阶段进度与归档（Phase 4，以 stage-progress 为唯一事实源） ----------

/**
 * 阶段内模板行（stages[].items[]）
 *
 * 双口径模型：
 * - currentCount / completed → 门禁口径（仅 SUBMITTED + ARCHIVED），驱动完成判断
 * - uploadCount → 展示口径（含 DRAFT），仅用于 UI 展示"已上传数量"
 */
export interface StageItemVO {
  evidenceTypeCode: string
  displayName: string
  required?: boolean
  isRequired?: boolean
  minCount: number
  /** 门禁口径：仅 SUBMITTED + ARCHIVED */
  currentCount: number
  /** 展示口径：DRAFT + SUBMITTED + ARCHIVED */
  uploadCount: number
  completed: boolean
  ruleGroup?: string | null
  groupCompleted?: boolean | null
  groupDisplayName?: string | null
  sortOrder?: number | null
}

/** 阶段进度（stages[] 元素） */
export interface StageVO {
  stageId?: number
  stageCode: string
  stageName: string
  stageDescription?: string
  itemCount: number
  completedCount: number
  /** 无必填项阶段（如 S2 无采购时）的展示用数量，有则阶段头用此显示 */
  displayItemCount?: number | null
  displayCompletedCount?: number | null
  completionPercent: number
  healthStatus: string
  stageCompleted: boolean
  canComplete: boolean
  items: StageItemVO[]
}

/** 门禁失败时未满足项（missingItems / blockedByRequiredItems 元素） */
export interface BlockedByItemVO {
  stageCode?: string
  evidenceTypeCode?: string | null
  displayName?: string
  shortfall?: number | null
}

/** GET /api/projects/{id}/stage-progress 响应 */
export interface StageProgressVO {
  overallCompletionPercent: number
  keyMissing: string[]
  canArchive: boolean
  archiveBlockReason?: string | null
  stages: StageVO[]
  projectName?: string
  projectStatus?: string
  hasProcurement?: boolean | null
  blockedByStages?: string[]
  blockedByRequiredItems?: BlockedByItemVO[]
}

/** 阶段完成结果（成功或失败含缺失项） */
export interface StageCompleteResult {
  success: boolean
  message?: string
  missingItems?: BlockedByItemVO[]
}

/** 归档门禁失败时返回的 data 结构 */
export interface ArchiveBlockVO {
  archiveBlockReason?: string
  keyMissing?: string[]
  blockedByStages?: string[]
  blockedByRequiredItems?: BlockedByItemVO[]
}

/** 阶段进度（唯一事实源） */
export const getStageProgress = (projectId: number) =>
  http.get<ApiResult<StageProgressVO>>(`/projects/${projectId}/stage-progress`)

/** 阶段完成（门禁失败时 code=400，data 为 StageCompleteResult） */
export const completeStage = (projectId: number, stageCode: string) =>
  http.post<ApiResult<StageCompleteResult>>(`/projects/${projectId}/stages/${stageCode}/complete`)

/** 项目归档（门禁失败时 code=400，data 为 ArchiveBlockVO） */
export const archiveProject = (projectId: number) =>
  http.post<ApiResult<ArchiveBlockVO | null>>(`/projects/${projectId}/archive`)

/** 申请归档（审批流）：创建 PENDING_APPROVAL 申请，项目状态变为 pending_approval */
export const archiveApply = (projectId: number) =>
  http.post<ApiResult<{ id: number }>>(`/projects/${projectId}/archive-apply`)

/** 审批通过（仅 PMO/SYSTEM_ADMIN）：通过后执行归档 */
export const archiveApprove = (projectId: number) =>
  http.post<ApiResult<null>>(`/projects/${projectId}/archive-approve`)

/** 退回归档申请（仅 PMO/SYSTEM_ADMIN），body 必填 comment，可选 evidenceComments 附件级不符合项 */
export const archiveReject = (
  projectId: number,
  data: { comment: string; evidenceComments?: { evidenceId: number; comment: string }[] }
) => http.post<ApiResult<null>>(`/projects/${projectId}/archive-reject`, data)

/** 项目待办汇总（项目列表页顶部工作台） */
export interface ProjectTodoSummaryVO {
  returnedCount: number
  pendingApprovalCount: number
}

export const getProjectTodoSummary = () =>
  http.get<ApiResult<ProjectTodoSummaryVO>>('/projects/todo-summary')

/** 归档审批历史：证据级退回明细 */
export interface RejectEvidenceDetailVO {
  evidenceId: number
  evidenceName: string
  stageName: string
  rejectComment: string
}

/** 归档审批历史：单条申请记录 */
export interface ProjectArchiveHistoryVO {
  applicationId: number
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
  applicantDisplayName: string
  approverDisplayName: string
  submitTime: string
  operationTime: string | null
  rejectComment: string | null
  rejectEvidences: RejectEvidenceDetailVO[]
}

/** 获取项目归档审批历史（历次申请/通过/退回及证据级不符合项） */
export const getArchiveHistory = (projectId: number) =>
  http.get<ApiResult<ProjectArchiveHistoryVO[]>>(`/projects/${projectId}/archive-history`)

/** 从接口响应或 axios 错误中取出 400 结构化 data（供弹窗/页面展示） */
export function getStructuredErrorData(
  resOrErr: { code?: number; data?: unknown } | { response?: { data?: { code?: number; data?: unknown; message?: string } } }
): { message: string; data: unknown } | null {
  const body = 'code' in resOrErr ? resOrErr : resOrErr.response?.data
  if (!body || (body as { code?: number }).code !== 400) return null
  const b = body as { message?: string; data?: unknown }
  return { message: b.message ?? '请求失败', data: b.data }
}

/** 项目导入结果 */
export interface ProjectImportResult {
  total: number
  inserted: number
  updated: number
  skipped: number
  errors: { row: number; code: string; message: string }[]
}

/** 下载导入模板（同源请求，与 http baseURL 一致） */
export const getProjectImportTemplateUrl = () => {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  const baseURL = base !== '/api' && !base.endsWith('/api') ? base.replace(/\/?$/, '') + '/api' : base
  return `${baseURL.startsWith('http') ? baseURL : (typeof window !== 'undefined' ? window.location.origin : '') + baseURL}/projects/import/template`
}

/** PMO 批量导入项目（仅 SYSTEM_ADMIN/PMO） */
export const importProjects = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResult<ProjectImportResult>>('/projects/import', form)
}
