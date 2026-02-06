import http from './http'

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface AuthUserSimpleVO {
  id: number
  username: string
  displayName?: string
}

/** 用户列表（sys_user），用于成员选择器等 */
export const getUsers = () => http.get<ApiResult<AuthUserSimpleVO[]>>('/users')
