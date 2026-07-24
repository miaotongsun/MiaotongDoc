/**
 * lib/download.ts —— 文件下载工具(Phase 14 U1:统一命名 + 去重)
 *
 * 命名规范:`${title}_${op}_${YYYYMMDD-HHmmss}.${ext}`
 * 重名处理:`xxx.pdf` 已下载过则加 ` (1)`,` (2)` ...
 *
 * 用法:
 *   const name = buildDownloadName('压缩', 'pdf', 'doc')
 *   downloadBlob(blob, name) // 自动去重
 *   triggerDownload(blob, '压缩', 'pdf', 'doc') // 一步到位
 */

const usedNames = new Set<string>()

/** 清空去重记录(新会话开始时调用) */
export function resetDownloadDedup() {
  usedNames.clear()
}

/** 构造规范文件名:`${title}_${op}_${YYYYMMDD-HHmmss}.${ext}` */
export function buildDownloadName(op: string, ext: string, title = 'document'): string {
  const safe = (s: string) =>
    s.replace(/[\\/:*?"<>|]/g, '_').trim().replace(/\s+/g, '_') || 'document'
  const t = safe(title.replace(/\.pdf$/i, ''))
  const o = safe(op)
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `${t}_${o}_${ts}.${ext}`
}

/** 同名自动加 (1)/(2) 后缀,确保本次会话内不重复 */
export function dedupeFilename(name: string): string {
  if (!usedNames.has(name)) {
    usedNames.add(name)
    return name
  }
  const dot = name.lastIndexOf('.')
  const base = dot > 0 ? name.slice(0, dot) : name
  const ext = dot > 0 ? name.slice(dot) : ''
  for (let i = 1; i < 100; i++) {
    const candidate = `${base} (${i})${ext}`
    if (!usedNames.has(candidate)) {
      usedNames.add(candidate)
      return candidate
    }
  }
  usedNames.add(name)
  return name
}

/** 触发浏览器下载(Blob → a.click) */
export function triggerDownload(blob: Blob, defaultName: string) {
  const name = dedupeFilename(defaultName)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 0)
}

/** 一步式:构造文件名 + 触发下载(op/ext/title 直接传入) */
export function downloadAs(blob: Blob, op: string, ext: string, title = 'document') {
  const name = buildDownloadName(op, ext, title)
  triggerDownload(blob, name)
}