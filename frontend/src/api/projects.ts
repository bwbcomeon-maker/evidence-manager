import http from './http'

export interface ProjectVO {
  id: number
  code: string
  name: string
  description: string
  status: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface CreateProjectBody {
  name: string
  description?: string
}

export const createProject = (body: CreateProjectBody) =>
  http.post<ApiResult<ProjectVO>>('/projects', body)
