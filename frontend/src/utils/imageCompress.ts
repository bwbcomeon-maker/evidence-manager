/**
 * 前端图片轻度压缩：仅对图片类型处理，最大宽度 1920px，质量 0.8
 * 非图片文件直接返回原文件
 */

const MAX_WIDTH = 1920
const QUALITY_JPEG = 0.8

/** 可压缩的图片 MIME（使用 Canvas 可绘制的格式） */
const COMPRESSIBLE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

function isCompressibleImage(file: File): boolean {
  const type = (file.type || '').toLowerCase()
  return COMPRESSIBLE_TYPES.some((t) => type === t || type.startsWith(t))
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片加载失败'))
    }
    img.src = url
  })
}

function drawToBlob(canvas: HTMLCanvasElement, mime: string, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob)
        else reject(new Error('压缩失败'))
      },
      mime,
      mime === 'image/png' ? undefined : quality
    )
  })
}

/**
 * 对图片进行轻度压缩：最大宽度 1920px，质量 0.8（png 不设质量）
 * 非图片或不可压缩类型直接返回原文件
 */
export async function compressImageIfNeeded(file: File): Promise<File> {
  if (!file || !(file instanceof File)) return file
  if (!isCompressibleImage(file)) return file

  const img = await loadImage(file)
  const { width, height } = img
  if (width <= 0 || height <= 0) return file

  const scale = width > MAX_WIDTH ? MAX_WIDTH / width : 1
  const w = Math.round(width * scale)
  const h = Math.round(height * scale)

  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return file
  ctx.drawImage(img, 0, 0, w, h)

  const mime = (file.type || 'image/jpeg').toLowerCase()
  const outMime = mime === 'image/webp' ? 'image/jpeg' : mime
  const quality = outMime === 'image/png' ? undefined : QUALITY_JPEG
  const blob = await drawToBlob(canvas, outMime, quality)

  const ext = file.name.includes('.') ? file.name.slice(file.name.lastIndexOf('.')) : ''
  const baseName = file.name.replace(/\.[^.]+$/i, '')
  const newName = outMime === 'image/png' ? `${baseName}${ext}` : `${baseName}.jpg`
  return new File([blob], newName, { type: blob.type })
}
