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

/** 业务类型代码与中文映射（与上传页一致） */
export const BIZ_TYPE_LABELS: Record<string, string> = {
  PLAN: '方案',
  REPORT: '报告',
  MINUTES: '纪要',
  TEST: '测试',
  ACCEPTANCE: '验收',
  OTHER: '其他'
}

export interface EvidenceListItem {
  evidenceId: number
  projectId: number
  title: string
  bizType?: string
  /** 备注（上传时填写） */
  note?: string
  contentType: string
  status?: string
  evidenceStatus?: EvidenceStatus
  createdByUserId?: number
  /** 上传人展示名（后端返回） */
  createdByDisplayName?: string
  createdAt: string
  updatedAt: string
  permissions?: PermissionBits
  canInvalidate?: boolean
  /** 作废原因/人/时间（INVALID 时有值） */
  invalidReason?: string
  invalidByUserId?: number
  /** 作废人展示名（后端可选返回） */
  invalidByDisplayName?: string
  invalidAt?: string
  /** 按阶段+模板项列表接口返回 */
  stageId?: number
  stageCode?: string
  evidenceTypeCode?: string
  latestVersion: {
    versionId: number
    versionNo: number
    originalFilename: string
    filePath: string
    fileSize: number
    createdAt: string
  } | null
}

/** 按阶段+模板项证据实例列表（GET .../stages/{stageCode}/evidence-types/{evidenceTypeCode}/evidences） */
export const getEvidencesByStageType = (
  projectId: number,
  stageCode: string,
  evidenceTypeCode: string
) =>
  http.get<{ code: number; message: string; data: EvidenceListItem[] }>(
    `/projects/${projectId}/stages/${stageCode}/evidence-types/${evidenceTypeCode}/evidences`
  )

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
