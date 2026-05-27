export function escapeHtml(str: string): string {
  const div = document.createElement('div')
  div.appendChild(document.createTextNode(str))
  return div.innerHTML
}

export function renderMentions(content: string): string {
  // First, escape ALL HTML to prevent XSS
  let safe = escapeHtml(content)

  // Then restore mention tags (they were escaped, so match escaped format)
  safe = safe.replace(/@\{userId:(\d+):([^}]+)\}/g, (_, id, name) => {
    return `<span class="mention-tag" data-user-id="${id}">@${name}</span>`
  })

  return safe
}
