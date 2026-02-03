import http from './http'

export interface ProjectVO {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdAt?: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface CreateProjectBody {
  code: string
  name: string
  description?: string
}

/** 项目列表（当前用户可见） */
export const getProjects = () =>
  http.get<ApiResult<ProjectVO[]>>('/projects')

/** 项目详情（不可见返回 403） */
export const getProjectDetail = (id: number) =>
  http.get<ApiResult<ProjectVO>>(`/projects/${id}`)

export const createProject = (body: CreateProjectBody) =>
  http.post<ApiResult<ProjectVO>>('/projects', body)
