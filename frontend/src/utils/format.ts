/**
 * 统一时间格式化（YYYY-MM-DD HH:mm）
 * 若项目已安装 dayjs，可改为: import dayjs from 'dayjs'; return dayjs(str).isValid() ? dayjs(str).format('YYYY-MM-DD HH:mm') : '—'
 */
export function formatDateTime(str: string): string {
  if (!str) return '—'
  const d = new Date(str)
  return isNaN(d.getTime())
    ? '—'
    : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
