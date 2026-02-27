/**
 * 将接口/axios 错误转为界面友好的中文提示，避免直接展示 "Request failed with status code 403" 等原始信息。
 */

const STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  401: '请先登录',
  403: '无权限执行此操作',
  404: '资源不存在',
  408: '请求超时，请稍后重试',
  500: '服务异常，请稍后重试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务繁忙，请稍后重试'
}

function isAxiosLike(e: unknown): e is { response?: { status?: number; data?: { message?: string } }; message?: string } {
  return typeof e === 'object' && e !== null
}

/**
 * 从接口错误或 axios 错误中取友好提示文案
 * @param error  catch 到的错误（可为 axios 错误或 { message, response }）
 * @param fallback 无有效信息时使用的默认文案
 */
export function getFriendlyErrorMessage(error: unknown, fallback = '操作失败'): string {
  if (error == null) return fallback
  if (isAxiosLike(error)) {
    const data = error.response?.data
    if (data && typeof data === 'object' && typeof (data as { message?: string }).message === 'string') {
      const msg = (data as { message: string }).message.trim()
      if (msg) return msg
    }
    const status = error.response?.status
    if (typeof status === 'number' && STATUS_MESSAGES[status]) return STATUS_MESSAGES[status]
    const msg = error.message
    if (typeof msg === 'string' && msg.trim()) {
      const codeMatch = msg.match(/Request failed with status code (\d+)/)
      if (codeMatch) {
        const code = Number(codeMatch[1])
        return STATUS_MESSAGES[code] ?? '请求失败，请稍后重试'
      }
      if (msg.includes('Network Error') || msg.includes('network')) return '网络异常，请检查连接后重试'
      if (msg.includes('timeout')) return '请求超时，请稍后重试'
      return msg
    }
  }
  if (error instanceof Error && error.message?.trim()) return error.message.trim()
  return fallback
}
