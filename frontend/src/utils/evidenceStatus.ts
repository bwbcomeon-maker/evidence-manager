/**
 * 证据状态统一：显示与映射
 * - 状态来源：evidenceStatus 优先，缺失时用 status（兼容旧字段）
 * - 全项目列表/弹窗/详情均用此工具，保证三处一致
 */

export type EvidenceStatusValue = 'DRAFT' | 'SUBMITTED' | 'ARCHIVED' | 'INVALID'

export interface EvidenceWithStatus {
  evidenceStatus?: string | null
  status?: string | null
}

/** 取有效状态：evidenceStatus 优先，否则 status */
export function getEffectiveEvidenceStatus(e: EvidenceWithStatus | string | null | undefined): string | null {
  if (e == null) return null
  if (typeof e === 'string') return e || null
  const s = e.evidenceStatus ?? e.status ?? null
  return s && String(s).trim() ? String(s).trim() : null
}

/** 状态 → 展示文案 */
export function mapStatusToText(status: string | null | undefined): string {
  if (!status) return '—'
  const m: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    ARCHIVED: '已归档',
    INVALID: '已作废',
    invalid: '已作废',
    archived: '已归档',
    active: '有效'
  }
  return m[status] ?? status
}

/** 状态 → Vant Tag type */
export function statusTagType(status: string | null | undefined): 'success' | 'danger' | 'default' | 'primary' {
  if (!status) return 'default'
  if (status === 'INVALID' || status === 'invalid') return 'danger'
  if (status === 'ARCHIVED' || status === 'archived') return 'success'
  if (status === 'SUBMITTED') return 'primary'
  return 'default'
}
