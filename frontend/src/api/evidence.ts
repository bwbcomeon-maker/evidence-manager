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
  /** 按阶段+模板项列表接口返回；归档确认弹窗用 stageName + evidenceTypeDisplayName 展示路径 */
  stageId?: number
  stageCode?: string
  stageName?: string
  evidenceTypeCode?: string
  evidenceTypeDisplayName?: string
  /** 归档退回时 PMO 标注的不符合原因（项目 returned 时后端返回） */
  rejectComment?: string
  latestVersion: {
    versionId: number
    versionNo: number
    originalFilename: string
    filePath: string
    watermarkedFilePath?: string
    watermarkedFilename?: string
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
  /** 证据状态筛选，与后端 evidenceStatus 参数对应 */
  evidenceStatus?: string
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

/** 全局证据搜索结果项（GET /api/evidence/global-search 返回的 data.records 元素） */
export interface EvidenceSearchResultItem {
  evidenceId: number
  projectId: number
  projectName?: string
  stageCode: string
  stageName?: string
  evidenceTypeCode: string
  evidenceTypeDisplayName?: string
  title: string
  createdByDisplayName?: string
  createdAt: string
  evidenceStatus?: string
  latestVersion?: {
    versionId: number
    versionNo: number
    originalFilename: string
    filePath: string
    watermarkedFilePath?: string
    watermarkedFilename?: string
    fileSize: number
    createdAt: string
  } | null
}

/** 全局证据搜索（GET /api/evidence/global-search） */
export const getEvidenceGlobalSearch = (params: {
  keyword: string
  page?: number
  pageSize?: number
}) => {
  return http.get<{
    code: number
    message: string
    data: PageResult<EvidenceSearchResultItem>
  }>('/evidence/global-search', { params })
}

// 证据状态流转
export const submitEvidence = (id: number) => http.post<{ code: number; message: string }>(`/evidence/${id}/submit`)
/** 草稿证据物理删除（仅 DRAFT 可删） */
export const deleteEvidence = (id: number) => http.delete<{ code: number; message: string }>(`/evidence/${id}`)
export const invalidateEvidence = (id: number, invalidReason: string) =>
  http.post<{ code: number; message: string }>(`/evidence/${id}/invalidate`, { invalidReason })

// 上传证据（支持上传进度回调，用于展示真实进度条）
export const uploadEvidence = (
  projectId: number,
  formData: FormData,
  options?: { onUploadProgress?: (percent: number) => void }
) => {
  return http.post(`/projects/${projectId}/evidences`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress: options?.onUploadProgress
      ? (progressEvent: { loaded: number; total?: number }) => {
          const total = progressEvent.total ?? 0
          const percent = total > 0 ? Math.round((progressEvent.loaded * 100) / total) : 0
          options.onUploadProgress!(percent)
        }
      : undefined
  })
}

// 下载证据版本文件（默认水印图优先）
export const downloadVersionFile = (versionId: number) => {
  return http.get(`/evidence/versions/${versionId}/download`, {
    responseType: 'blob'
  }) as Promise<Blob>
}

/** 下载原图（需后端配置 evidence.image.original-access-enabled=true 且角色为 SYSTEM_ADMIN/PMO） */
export const getOriginalDownloadUrl = (versionId: number): string => {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  const apiBase = base !== '/api' && !base.endsWith('/api') ? base.replace(/\/?$/, '') + '/api' : base
  const path = apiBase.endsWith('/') ? apiBase.slice(0, -1) : apiBase
  return `${path}/evidence/versions/${versionId}/download?variant=ORIGINAL`
}
