import http from './http'

/** 证据生命周期状态 */
export type EvidenceStatus = 'DRAFT' | 'SUBMITTED' | 'ARCHIVED' | 'INVALID'

/** V1 统一权限位（与后端同源） */
export interface PermissionBits {
  canUpload?: boolean
  canSubmit?: boolean
  canArchive?: boolean
  canInvalidate?: boolean
  canManageMembers?: boolean
}

export interface EvidenceListItem {
  evidenceId: number
  projectId: number
  title: string
  bizType: string
  contentType: string
  status: string
  evidenceStatus?: EvidenceStatus
  createdByUserId?: number
  createdAt: string
  updatedAt: string
  permissions?: PermissionBits
  canInvalidate?: boolean
  /** 作废原因/人/时间（INVALID 时有值） */
  invalidReason?: string
  invalidByUserId?: number
  invalidAt?: string
  latestVersion: {
    versionId: number
    versionNo: number
    originalFilename: string
    filePath: string
    fileSize: number
    createdAt: string
  } | null
}

export interface EvidenceListParams {
  nameLike?: string
  status?: string
  bizType?: string
  contentType?: string
}

/** 全局证据列表查询参数（GET /api/evidence） */
export interface EvidenceGlobalListParams {
  page?: number
  pageSize?: number
  projectId?: number
  status?: string
  uploader?: string
  recentDays?: number
  fileCategory?: 'image' | 'document' | 'video'
  nameLike?: string
}

export interface PageResult<T> {
  total: number
  records: T[]
  page: number
  pageSize: number
}

export interface EvidenceListResponse {
  code: number
  message: string
  data: PageResult<EvidenceListItem>
}

export interface EvidenceUploadParams {
  name: string
  type: string
  remark?: string
  file: File
}

// 获取证据列表（按项目）
export const getEvidenceList = (projectId: number, params?: EvidenceListParams) => {
  return http.get<{ code: number; message: string; data: EvidenceListItem[] }>(
    `/projects/${projectId}/evidences`,
    { params }
  )
}

// 全局证据分页列表（GET /api/evidence）
export const listEvidence = (params?: EvidenceGlobalListParams) => {
  return http.get<EvidenceListResponse>('/evidence', { params })
}

// 证据详情（GET /api/evidence/{id}）
export const getEvidenceById = (id: number) => {
  return http.get<{ code: number; message: string; data: EvidenceListItem }>(`/evidence/${id}`)
}

// 证据状态流转
export const submitEvidence = (id: number) => http.post<{ code: number; message: string }>(`/evidence/${id}/submit`)
export const archiveEvidence = (id: number) => http.post<{ code: number; message: string }>(`/evidence/${id}/archive`)
export const invalidateEvidence = (id: number, invalidReason: string) =>
  http.post<{ code: number; message: string }>(`/evidence/${id}/invalidate`, { invalidReason })

// 上传证据
export const uploadEvidence = (projectId: number, formData: FormData) => {
  return http.post(`/projects/${projectId}/evidences`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 下载证据版本文件
export const downloadVersionFile = (versionId: number) => {
  return http.get(`/evidence/versions/${versionId}/download`, {
    responseType: 'blob'
  }) as Promise<Blob>
}
