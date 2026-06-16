/**
 * MiaotongDoc Yjs WebSocket 协同编辑服务器
 *
 * 为 Markdown 和 PDF 注释提供实时 CRDT 协同同步。
 * 每个文档通过 docName 隔离（如 "md-{docKey}" 或 "pdf-{docKey}"）。
 */

const http = require('http')
const ws = require('ws')
const { setupWSConnection } = require('y-websocket/bin/utils')

const PORT = process.env.YJS_PORT || 1234

const server = http.createServer((req, res) => {
  // 健康检查端点
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ status: 'ok', service: 'miaotongdoc-yjs' }))
    return
  }
  res.writeHead(404)
  res.end()
})

const wss = new ws.Server({ server })

wss.on('connection', (ws, req) => {
  // 从 URL 提取文档名：/ws/yjs/{docName}
  const url = new URL(req.url, `http://${req.headers.host}`)
  const pathParts = url.pathname.split('/').filter(Boolean)

  // 支持 /ws/yjs/docName 或 /docName 格式
  let docName = pathParts[pathParts.length - 1] || 'default'

  // 安全校验：只允许合法的文档名格式
  if (!docName.match(/^(md|pdf)-[a-f0-9-]+$/)) {
    console.warn(`[Yjs] 拒绝非法文档名: ${docName}`)
    ws.close(4000, 'Invalid document name')
    return
  }

  console.log(`[Yjs] 新连接: docName=${docName}, ip=${req.socket.remoteAddress}`)

  setupWSConnection(ws, req, {
    docName,
    gc: true, // 启用垃圾回收
  })

  ws.on('close', () => {
    console.log(`[Yjs] 连接关闭: docName=${docName}`)
  })
})

server.listen(PORT, () => {
  console.log(`[Yjs] 协同编辑服务器已启动，端口: ${PORT}`)
})

// 优雅关闭
process.on('SIGTERM', () => {
  console.log('[Yjs] 收到 SIGTERM，正在关闭...')
  wss.close()
  server.close(() => process.exit(0))
})

process.on('SIGINT', () => {
  console.log('[Yjs] 收到 SIGINT，正在关闭...')
  wss.close()
  server.close(() => process.exit(0))
})
