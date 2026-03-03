import http from './http'

export interface TodoItemVO {
  id: number
  type: string
  title: string
  body: string | null
  relatedProjectId: number | null
  relatedApplicationId: number | null
  linkPath: string | null
  readAt: string | null
  createdAt: string
}

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 待办列表，支持未读筛选与类型筛选 */
export const getTodos = (params?: { unreadOnly?: boolean; type?: string; limit?: number }) =>
  http.get<ApiResult<TodoItemVO[]>>('/notifications/todos', { params })

/** 单条标记已读 */
export const markTodoRead = (id: number) =>
  http.patch<ApiResult<null>>(`/notifications/${id}/read`)
