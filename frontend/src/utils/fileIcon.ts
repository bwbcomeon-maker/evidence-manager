/**
 * 文件类型图标与展示：证据列表、按类型查看等统一使用
 */

export interface FileIconConfig {
  icon: string
  label: string
  color: string
  bg: string
}

/** 根据后缀返回图标配置；文档类使用 PDF/WORD/EXCEL/PPT/FILE 等大写文字标识 */
export function getFileIconConfig(fileName?: string): FileIconConfig {
  const ext = (fileName || '').split('.').pop()?.toLowerCase() || ''
  switch (ext) {
    case 'pdf':
      return { icon: 'description', label: 'PDF', color: '#ef4444', bg: '#fef2f2' }
    case 'doc':
    case 'docx':
      return { icon: 'description', label: 'WORD', color: '#3b82f6', bg: '#eff6ff' }
    case 'xls':
    case 'xlsx':
    case 'csv':
      return { icon: 'bar-chart-o', label: 'EXCEL', color: '#22c55e', bg: '#f0fdf4' }
    case 'ppt':
    case 'pptx':
      return { icon: 'photo-o', label: 'PPT', color: '#f97316', bg: '#fff7ed' }
    case 'zip':
    case 'rar':
    case '7z':
    case 'tar':
    case 'gz':
      return { icon: 'gift-o', label: 'ZIP', color: '#eab308', bg: '#fefce8' }
    case 'txt':
    case 'md':
      return { icon: 'notes-o', label: 'TXT', color: '#969799', bg: '#f7f8fa' }
    case 'jpg':
    case 'jpeg':
    case 'png':
    case 'gif':
    case 'webp':
    case 'bmp':
      return { icon: 'photo-o', label: '图片', color: '#007AFF', bg: '#e8f4ff' }
    case 'mp4':
    case 'webm':
    case 'mov':
    case 'avi':
    case 'mkv':
      return { icon: 'play-circle-o', label: '视频', color: '#007AFF', bg: '#e8f4ff' }
    default:
      return { icon: 'description', label: ext ? 'FILE' : 'FILE', color: '#969799', bg: '#f7f8fa' }
  }
}

export function isImageType(contentType?: string): boolean {
  return !!contentType && contentType.startsWith('image/')
}

export function isVideoType(contentType?: string): boolean {
  return !!contentType && contentType.startsWith('video/')
}

export function getEvidenceFileName(item: { latestVersion?: { originalFilename: string } | null; title?: string }): string {
  return item.latestVersion?.originalFilename || item.title || ''
}
