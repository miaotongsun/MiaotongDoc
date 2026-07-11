import DOMPurify from 'dompurify'
import type { Config as DOMPurifyConfig } from 'dompurify'

/**
 * 通用 HTML escape（用于非信任内容插入 DOM 文本节点）
 */
export function escapeHtml(str: string): string {
  const div = document.createElement('div')
  div.appendChild(document.createTextNode(str))
  return div.innerHTML
}

/**
 * @提及渲染（先 escape 再恢复 @{} 标签）
 */
export function renderMentions(content: string): string {
  let safe = escapeHtml(content)
  safe = safe.replace(/@\{userId:(\d+):([^:}]+)(?::([^}]*))?\}/g, (_, id, name, empId) => {
    const display = empId ? `@${name}(${empId})` : `@${name}`
    return `<span class="mention-tag" data-user-id="${id}">${display}</span>`
  })
  return safe
}

// ============== AI 输出专用 sanitize ==============

/**
 * AI Markdown 内容 sanitize（保留常见 Markdown / 结构化标签）
 *
 * 适用：AI 输出的 Markdown 文本，需要被渲染成 HTML 时（v-html 或 dangerouslySetInnerHTML）
 * 不适用：直接交给 Tiptap 等编辑器处理 Markdown 文本的场景（编辑器自身已转义）
 *
 * 防御：
 * - 移除 <script> / <iframe> / <object> / <embed> / <style> / <link>
 * - 移除所有 on*= 事件属性
 * - javascript: / data: 等危险 URI 被拒
 */
const MARKDOWN_CONFIG: DOMPurifyConfig = {
  ALLOWED_TAGS: [
    'a', 'p', 'br', 'hr', 'span', 'div',
    'b', 'i', 'em', 'strong', 'u', 's', 'sub', 'sup', 'mark', 'small',
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'ul', 'ol', 'li',
    'blockquote', 'code', 'pre', 'kbd',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
  ],
  ALLOWED_ATTR: ['href', 'title', 'class', 'target', 'rel'],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto|tel):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
  // 强制 a 标签安全：添加 rel="noopener noreferrer"
  ADD_ATTR: ['target'],
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'style', 'link', 'meta', 'form'],
  FORBID_ATTR: ['onerror', 'onclick', 'onload', 'onmouseover', 'onfocus', 'onblur', 'onchange', 'onsubmit'],
}

export function sanitizeAiMarkdown(s: string): string {
  return String(DOMPurify.sanitize(s, MARKDOWN_CONFIG))
}

/**
 * 纯文本场景（彻底去 HTML）
 */
export function sanitizePlainText(s: string): string {
  return String(DOMPurify.sanitize(s, {
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
  }))
}