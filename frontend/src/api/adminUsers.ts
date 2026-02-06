import http from './http'

export interface AdminUserItem {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  roleCode: string
  enabled: boolean
  createdAt: string
}

export interface PageResult<T> {
  total: number
  records: T[]
  page: number
  pageSize: number
}

export interface CreateUserBody {
  username: string
  password?: string
  realName?: string
  phone?: string
  email?: string
  roleCode: string
  enabled?: boolean
}

export interface UpdateUserBody {
  realName?: string
  phone?: string
  email?: string
  roleCode?: string
  enabled?: boolean
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 系统级角色（已废弃的 PROJECT_* 已移除，存量已迁移为 USER） */
const ROLE_OPTIONS = [
  { text: '系统管理员', value: 'SYSTEM_ADMIN' },
  { text: 'PMO（治理）', value: 'PMO' },
  { text: '审计（只读入口）', value: 'AUDITOR' },
  { text: '普通用户', value: 'USER' }
]

export { ROLE_OPTIONS }

export const getAdminUserPage = (params: {
  page?: number
  pageSize?: number
  keyword?: string
  roleCode?: string
  enabled?: boolean
}) => http.get<ApiResult<PageResult<AdminUserItem>>>('/admin/users', { params })

export const createAdminUser = (body: CreateUserBody) =>
  http.post<ApiResult<AdminUserItem>>('/admin/users', body)

export const updateAdminUser = (id: number, body: UpdateUserBody) =>
  http.put<ApiResult<AdminUserItem>>(`/admin/users/${id}`, body)

export const setAdminUserEnabled = (id: number, enabled: boolean) =>
  http.patch<ApiResult<null>>(`/admin/users/${id}/enable`, { enabled })

export const resetAdminUserPassword = (id: number) =>
  http.post<ApiResult<{ newPassword: string }>>(`/admin/users/${id}/reset-password`)

export const deleteAdminUser = (id: number) =>
  http.delete<ApiResult<null>>(`/admin/users/${id}`)
