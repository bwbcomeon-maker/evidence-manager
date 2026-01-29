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
