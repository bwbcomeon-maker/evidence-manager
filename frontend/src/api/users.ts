import http from './http'

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface AuthUserSimpleVO {
  id: string
  username: string
  displayName?: string
}

/** 用户列表（auth_user），用于成员选择器等 */
export const getUsers = () => http.get<ApiResult<AuthUserSimpleVO[]>>('/users')
