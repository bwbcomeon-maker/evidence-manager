import http from './http'

export interface EvidenceListItem {
  evidenceId: number
  projectId: number
  title: string
  bizType: string
  contentType: string
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
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

export interface EvidenceUploadParams {
  name: string
  type: string
  remark?: string
  file: File
}

// 获取证据列表
export const getEvidenceList = (projectId: number, params?: EvidenceListParams) => {
  return http.get<{ code: number; message: string; data: EvidenceListItem[] }>(
    `/projects/${projectId}/evidences`,
    { params }
  )
}

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
