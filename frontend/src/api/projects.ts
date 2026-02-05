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
  status: string
  createdAt?: string
  /** V1 统一权限位 */
  permissions?: PermissionBits
  canInvalidate?: boolean
  canManageMembers?: boolean
  canUpload?: boolean
  /** 当前项目经理 userId（UUID 字符串） */
  currentPmUserId?: string
  /** 当前项目经理展示名 */
  currentPmDisplayName?: string
}

export interface ProjectMemberVO {
  userId: string
  role: string
  username?: string
  displayName?: string
  /** 是否为当前登录用户（当前用户不显示编辑/移除） */
  isCurrentUser?: boolean
}

export interface AddProjectMemberBody {
  userId: string
  role: 'owner' | 'editor' | 'viewer'
}

/** 项目成员列表 */
export const getProjectMembers = (projectId: number) =>
  http.get<ApiResult<ProjectMemberVO[]>>(`/projects/${projectId}/members`)

/** 添加或调整项目成员 */
export const addOrUpdateProjectMember = (projectId: number, body: AddProjectMemberBody) =>
  http.post<ApiResult<unknown>>(`/projects/${projectId}/members`, body)

/** 移除项目成员 */
export const removeProjectMember = (projectId: number, userId: string) =>
  http.delete<ApiResult<unknown>>(`/projects/${projectId}/members/${userId}`)

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface CreateProjectBody {
  code: string
  name: string
  description?: string
}

/** 项目列表（当前用户可见） */
export const getProjects = () =>
  http.get<ApiResult<ProjectVO[]>>('/projects')

/** 项目详情（不可见返回 403） */
export const getProjectDetail = (id: number) =>
  http.get<ApiResult<ProjectVO>>(`/projects/${id}`)

export const createProject = (body: CreateProjectBody) =>
  http.post<ApiResult<ProjectVO>>('/projects', body)

/** 项目导入结果（最小版） */
export interface ProjectImportResult {
  total: number
  successCount: number
  failCount: number
  details: { row: number; code: string; success: boolean; message: string }[]
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
