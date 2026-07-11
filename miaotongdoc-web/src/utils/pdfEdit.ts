/**
 * PDF 编辑工具类
 * 使用 pdf-lib 实现 PDF 内容编辑
 */

export interface PdfTextEdit {
  id: string
  type: 'add' | 'delete' | 'modify'
  pageNum: number
  // 添加/修改文字
  text?: string
  x?: number
  y?: number
  fontSize?: number
  color?: string // hex
  font?: 'Helvetica' | 'Helvetica-Bold' | 'Times-Roman' | 'Times-Bold'
  // 删除文字：用白色矩形覆盖
  deleteRect?: { x: number; y: number; width: number; height: number }
  // 原始文字信息（用于修改）
  originalText?: string
  originalX?: number
  originalY?: number
}

export interface PdfEditOperation {
  docId: number
  edits: PdfTextEdit[]
  timestamp: string
}

/**
 * 将屏幕坐标转换为 PDF 坐标
 * PDF 坐标原点在左下角，屏幕在左上角
 */
export function screenToPdfCoord(
  screenX: number,
  screenY: number,
  _pageWidth: number,
  pageHeight: number,
  scale: number
) {
  // 屏幕坐标 -> PDF 坐标
  const pdfX = screenX / scale
  const pdfY = (pageHeight - screenY / scale) // 翻转 Y 轴
  return { x: pdfX, y: pdfY }
}

/**
 * PDF 坐标转屏幕坐标
 */
export function pdfToScreenCoord(
  pdfX: number,
  pdfY: number,
  _pageWidth: number,
  pageHeight: number,
  scale: number
) {
  const screenX = pdfX * scale
  const screenY = (pageHeight - pdfY) * scale
  return { x: screenX, y: screenY }
}

/**
 * 生成唯一 ID
 */
export function generateEditId(): string {
  return `edit-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

/**
 * 默认字体配置
 */
export const DEFAULT_FONT_CONFIG = {
  font: 'Helvetica' as const,
  fontSize: 14,
  color: '#000000',
}

/**
 * 颜色工具
 */
export function hexToRgb(hex: string): { r: number; g: number; b: number } {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16),
  } : { r: 0, g: 0, b: 0 }
}

/**
 * 获取字体
 */
export function getFont(pdfDoc: any, fontName: string): any {
  switch (fontName) {
    case 'Helvetica-Bold':
      return pdfDoc.fonts.bold
    case 'Times-Roman':
      return pdfDoc.fonts.serif
    case 'Times-Bold':
      return pdfDoc.fonts.serifBold
    default:
      return pdfDoc.fonts.helvetica
  }
}

/**
 * 应用编辑操作到 PDF
 * @param pdfBytes PDF 文件字节数组
 * @param edits 编辑操作列表
 * @returns 修改后的 PDF 字节数组
 */
export async function applyEditsToPdf(
  pdfBytes: Uint8Array,
  edits: PdfTextEdit[]
): Promise<Uint8Array> {
  const { PDFDocument, rgb, StandardFonts } = await import('pdf-lib')
  const pdfDoc = await PDFDocument.load(pdfBytes)

  // pdf-lib 的 fonts 属性是私有的，需要类型断言访问
  const fonts = (pdfDoc as any).fonts

  // 确保嵌入标准字体
  if (!fonts.helvetica) {
    fonts.helvetica = pdfDoc.embedFont(StandardFonts.Helvetica)
  }
  if (!fonts.bold) {
    fonts.bold = pdfDoc.embedFont(StandardFonts.HelveticaBold)
  }
  if (!fonts.serif) {
    fonts.serif = pdfDoc.embedFont(StandardFonts.TimesRoman)
  }
  if (!fonts.serifBold) {
    fonts.serifBold = pdfDoc.embedFont(StandardFonts.TimesRomanBoldItalic)
  }

  for (const edit of edits) {
    const pageNum = edit.pageNum - 1 // PDF-lib 从 0 开始
    if (pageNum < 0 || pageNum >= pdfDoc.getPageCount()) continue

    const page = pdfDoc.getPage(pageNum)
    const { height: pageHeight } = page.getSize()

    if (edit.type === 'add' && edit.text) {
      // 添加文字
      const font = fonts[edit.font?.includes('Bold') ? 'bold' : 'helvetica']
      const color = hexToRgb(edit.color || '#000000')
      page.drawText(edit.text, {
        x: edit.x,
        y: pageHeight - (edit.y || 0), // 翻转 Y 轴
        size: edit.fontSize || DEFAULT_FONT_CONFIG.fontSize,
        font,
        color: rgb(color.r / 255, color.g / 255, color.b / 255),
      })
    } else if (edit.type === 'delete' && edit.deleteRect) {
      // 删除文字：用白色矩形覆盖
      const { x, y, width, height } = edit.deleteRect
      page.drawRectangle({
        x,
        y: pageHeight - y - height, // 翻转 Y 轴
        width,
        height,
        color: rgb(1, 1, 1), // 白色
        opacity: 1,
      })
    } else if (edit.type === 'modify' && edit.text && edit.originalX !== undefined) {
      // 修改文字：先覆盖原位置，再添加新文字
      const font = fonts[edit.font?.includes('Bold') ? 'bold' : 'helvetica']
      const color = hexToRgb(edit.color || '#000000')
      const fontSize = edit.fontSize || DEFAULT_FONT_CONFIG.fontSize

      // 覆盖原位置
      if (edit.originalText && edit.originalY !== undefined) {
        page.drawText(edit.originalText, {
          x: edit.originalX,
          y: pageHeight - edit.originalY,
          size: fontSize,
          font,
          color: rgb(1, 1, 1), // 白色覆盖
        })
      }

      // 添加新文字
      if (edit.y !== undefined) {
        page.drawText(edit.text, {
          x: edit.x,
          y: pageHeight - edit.y,
          size: fontSize,
          font,
          color: rgb(color.r / 255, color.g / 255, color.b / 255),
        })
      }
    }
  }

  return await pdfDoc.save()
}

/**
 * 从 PDF 提取可编辑文字信息
 * 用于识别 PDF 中的文字位置
 */
export async function extractTextWithPositions(
  pdfBytes: Uint8Array
): Promise<Array<{ pageNum: number; text: string; x: number; y: number; fontSize: number }>> {
  const results: Array<{ pageNum: number; text: string; x: number; y: number; fontSize: number }> = []

  // 使用 pdfjs-dist 提取文字位置
  const pdfjsLib = await import('pdfjs-dist')

  const blob = new Blob([pdfBytes as BlobPart], { type: 'application/pdf' })
  const url = URL.createObjectURL(blob)
  const loadingTask = pdfjsLib.getDocument(url)
  const pdf = await loadingTask.promise

  for (let i = 1; i <= pdf.numPages; i++) {
    const page = await pdf.getPage(i)
    const textContent = await page.getTextContent()

    for (const item of textContent.items as any[]) {
      if (item.str && item.str.trim()) {
        const transform = item.transform
        // transform: [scaleX, skewY, skewX, scaleY, translateX, translateY]
        const fontSize = Math.sqrt(transform[0] * transform[0] + transform[1] * transform[1])
        results.push({
          pageNum: i,
          text: item.str,
          x: transform[4],
          y: page.view[3] - transform[5], // 转换为 PDF 坐标系
          fontSize: Math.round(fontSize),
        })
      }
    }
  }

  URL.revokeObjectURL(url)
  return results
}

/**
 * 检查编辑是否有效
 */
export function isValidEdit(edit: PdfTextEdit): boolean {
  if (edit.type === 'add') {
    return !!edit.text && edit.x !== undefined && edit.y !== undefined
  } else if (edit.type === 'delete') {
    return !!edit.deleteRect
  } else if (edit.type === 'modify') {
    return !!edit.text && edit.x !== undefined && edit.y !== undefined
  }
  return false
}
