export function escapeHtml(str: string): string {
  const div = document.createElement('div')
  div.appendChild(document.createTextNode(str))
  return div.innerHTML
}

export function renderMentions(content: string): string {
  // First, escape ALL HTML to prevent XSS
  let safe = escapeHtml(content)

  // Then restore mention tags (they were escaped, so match escaped format)
  // Format: @{userId:X:Name:employeeId} or @{userId:X:Name}
  safe = safe.replace(/@\{userId:(\d+):([^:}]+)(?::([^}]*))?\}/g, (_, id, name, empId) => {
    const display = empId ? `@${name}(${empId})` : `@${name}`
    return `<span class="mention-tag" data-user-id="${id}">${display}</span>`
  })

  return safe
}
