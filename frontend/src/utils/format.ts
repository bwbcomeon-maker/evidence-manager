/**
 * 统一时间格式化
 * 使用 dayjs 解析 ISO 等格式，输出 YYYY-MM-DD HH:mm
 */
import dayjs from 'dayjs'

export function formatDateTime(str: string | null | undefined): string {
  if (str == null || String(str).trim() === '') return '—'
  const d = dayjs(str)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '—'
}

/** 带秒的完整时间格式（用于审批历史等需要精确到秒的展示） */
export function formatDateTimeFull(str: string | null | undefined): string {
  if (str == null || String(str).trim() === '') return '—'
  const d = dayjs(str)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : '—'
}
