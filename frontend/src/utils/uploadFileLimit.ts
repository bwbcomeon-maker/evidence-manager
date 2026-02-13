/**
 * 上传文件大小校验：图片类 5MB，文档类 50MB
 */

export const IMAGE_MAX_BYTES = 5 * 1024 * 1024   // 5MB
export const DOCUMENT_MAX_BYTES = 50 * 1024 * 1024 // 50MB

/** 图片类 MIME 前缀 */
const IMAGE_TYPE_PREFIX = 'image/'

/** 常见图片扩展名（用于无 type 时兜底） */
const IMAGE_EXT = new Set(
  ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico', 'heic', 'heif'].map((e) => e.toLowerCase())
)

/**
 * 判断是否为图片类文件（按 MIME 或扩展名）
 */
export function isImageFile(file: File): boolean {
  const type = (file.type || '').toLowerCase()
  if (type.startsWith(IMAGE_TYPE_PREFIX)) return true
  const name = (file.name || '').toLowerCase()
  const ext = name.includes('.') ? name.slice(name.lastIndexOf('.') + 1) : ''
  return IMAGE_EXT.has(ext)
}

/**
 * 获取该文件允许的最大字节数
 */
export function getFileSizeLimitBytes(file: File): number {
  return isImageFile(file) ? IMAGE_MAX_BYTES : DOCUMENT_MAX_BYTES
}

/**
 * 校验文件大小，返回 { ok, message }
 */
export function validateFileSize(file: File): { ok: true } | { ok: false; message: string } {
  const limit = getFileSizeLimitBytes(file)
  const size = file.size ?? 0
  if (size <= limit) return { ok: true }

  const limitMB = limit === IMAGE_MAX_BYTES ? 5 : 50
  const category = isImageFile(file) ? '图片' : '文档'
  return {
    ok: false,
    message: `${category}类文件单文件不能超过 ${limitMB}MB，当前文件约 ${(size / 1024 / 1024).toFixed(1)}MB`
  }
}
