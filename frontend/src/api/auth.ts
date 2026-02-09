import http from './http'

export interface AuthUser {
  id: number
  username: string
  realName: string
  roleCode: string
  enabled: boolean
}

export interface LoginBody {
  username: string
  password: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export const login = (body: LoginBody) =>
  http.post<ApiResult<AuthUser>>('/auth/login', body)

export const logout = () =>
  http.post<ApiResult<null>>('/auth/logout')

export const getMe = () =>
  http.get<ApiResult<AuthUser>>('/auth/me')

/** 验证当前用户原密码（修改密码前第一步校验），需登录 */
export const verifyPassword = (body: { password: string }) =>
  http.post<ApiResult<null>>('/auth/verify-password', body)

/** 自助修改密码，需登录 */
export interface ChangePasswordBody {
  oldPassword: string
  newPassword: string
}

export const changePassword = (body: ChangePasswordBody) =>
  http.post<ApiResult<null>>('/auth/change-password', body)
