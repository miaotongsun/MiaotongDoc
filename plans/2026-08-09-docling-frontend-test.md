# Docling 前端测试手册(2026-08-09)

> **前提**:Docling 已启用(`DOCLING_ENABLED=true`)+ AiStatusController bug 已修复(返回真实状态)

## 🎯 测试目标

验证 Docling 在前端 3 个场景生效,并能通过日志/结果特征确认"真的是 Docling 在跑"。

---

## ✅ 第 0 步:确认 Docling 已就绪

### 0.1 浏览器确认

打开 http://localhost (nginx),登录 `10000000` / `123456`。

按 F12 打开开发者工具 -> Network 面板 -> 刷新页面 -> 找 `status` 请求,响应应包含:
```json
"docling": {
  "configured": true,
  "available": true,
  "serverUrl": "http://docling:5001"
}
```

### 0.2 命令行确认(更可靠)

```bash
# 1. docling 容器在跑
docker ps --filter name=docling
# 应看到: miaotongdoc-docling   Up X minutes (healthy)

# 2. AI 状态接口
TOKEN=$(curl -s -X POST "http://localhost:9004/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"10000000","password":"123456"}' | python -c "import json,sys;print(json.load(sys.stdin)['token'])")
curl -s "http://localhost:9004/api/ai/status" -H "Authorization: Bearer $TOKEN" | python -m json.tool
# docling.available 应为 true
```

---

## 📋 第 1 步:准备测试 PDF

**最佳测试材料**:带章节标题 + 表格的 PDF(如合同、财务报告、产品手册)。

如果手头没有,可以用 Word 另存为 PDF,内容建议:
```
第一章 项目概述
(几段文字)

1.1 项目背景
(几段文字)

表1 费用清单
| 项目 | 金额 | 备注 |
| 服务费 | 100万 | 含税 |
| 硬件费 | 50万 | - |
```

上传到系统:首页 -> 「上传文档」-> 选 PDF。

---

## 🧪 第 2 步:3 个测试场景

### 场景 A:导出 Markdown(最直接,必走 Docling)

**操作**:
1. 首页打开刚上传的 PDF -> 进入 PDF 编辑器
2. 顶部工具栏 -> 「文件」组 -> 点「导出」按钮
3. 弹出菜单选「Markdown」
4. 浏览器下载一个 `.md` 文件

**预期(Docling 生效)**:
- ✅ markdown 里有 `#` / `##` / `###` 标题层级
- ✅ 表格被转成 markdown 表格语法 `| 项目 | 金额 |`
- ✅ 段落顺序正确(版面分析)

**对比(PDFBox 兜底,Docling 没生效时)**:
- ❌ 纯文本,无 `#` 标题
- ❌ 表格丢失或变成乱序文本
- ❌ 段落顺序可能错乱

**验证走的是 Docling**:
```bash
docker logs miaotongdoc-server --tail 30 | grep -i "docling"
# 应看到:  "调用 Docling 解析: docId=X, url=http://docling:5001/v1/convert/file"
#          "Docling 解析成功: docId=X, length=XXXX"
```

---

### 场景 B:AI 全文摘要(走 Docling 提取结构化文本)

**操作**:
1. PDF 编辑器 -> 顶部「AI」标签
2. 点「全文摘要」按钮
3. 等待 AI 返回摘要(右侧 AI 面板)

**预期(Docling 生效)**:
- ✅ AI 摘要能准确引用章节标题(因为输入是结构化 markdown)
- ✅ 摘要能提到表格里的数据(如金额)

**验证**:
```bash
docker logs miaotongdoc-server --tail 50 | grep -iE "docling|extractStructured"
# 应看到 Docling 解析日志(在 AI 调用之前)
```

**原理**:`AiService.documentQa` -> `DocumentContentService.extractStructured` -> `DoclingService.parse` -> Docling 输出 markdown -> 喂给 LLM。

---

### 场景 C:OCR 快速识别(4 层瀑布 L1 Docling)

**操作**:
1. PDF 编辑器 -> 顶部「识别」组
2. 点「OCR 快速识别」
3. 等待识别完成(状态条显示进度)

**预期**:
- ✅ 如果是文本型 PDF -> Docling 跑 L1,秒级返回带结构的结果
- ✅ 如果是扫描件 PDF -> Docling 内置 OCR 也能处理(慢一些)

**验证走了哪层**:
```bash
# 看后端日志,Docling 成功会显示:
docker logs miaotongdoc-server --tail 50 | grep -iE "docling|paddle|engine="
# "调用 Docling 解析" + "Docling 解析成功" = 走了 L1
# 如果只看到 PaddleOCR = Docling 挂了自动降级 L2
```

**注意**:这个场景用「OCR 快速识别」按钮走的是 `/recognize-paddle`(强制 PaddleOCR,绕过 Docling)。要走 Docling 必须用「AI -> 智能识别」或 `/recognize` 端点。前端目前「OCR 快速识别」按钮**不经过 Docling**。

---

## 🔬 第 3 步:对比测试(关闭 Docling 看差异)

临时关闭 Docling,跑同样场景,对比输出:

```bash
# 1. 临时关闭
sed -i 's/DOCLING_ENABLED=true/DOCLING_ENABLED=false/' MiaotongDoc-Docker/.env
cd MiaotongDoc-Docker && docker compose restart web-server
sleep 30

# 2. 跑场景 A(导出 Markdown),对比输出
#    关闭后:纯文本,无标题层级,表格丢失

# 3. 测完恢复
sed -i 's/DOCLING_ENABLED=false/DOCLING_ENABLED=true/' MiaotongDoc-Docker/.env
docker compose restart web-server
```

---

## 🐛 故障排查

### 问题:状态栏显示 Docling 未配置

```bash
# 检查 3 个点
docker ps --filter name=docling                    # 1. 容器在跑?
grep DOCLING_ENABLED MiaotongDoc-Docker/.env       # 2. env 是 true?
docker exec miaotongdoc-server env | grep DOCLING  # 3. web-server 读到 true?
```

### 问题:导出 Markdown 还是纯文本(Docling 没生效)

```bash
# 看后端日志,确认是否真的调了 Docling
docker logs miaotongdoc-server --tail 50 | grep -i docling

# 如果看到 "Docling 服务不可用" -> 检查容器间网络
docker exec miaotongdoc-server curl -s http://docling:5001/health
# 应返回 {"status":"ok"}

# 如果看到 "Docling 解析失败,回退到 PDFTextStripper" -> Docling 处理该 PDF 报错
docker logs miaotongdoc-docling --tail 30          # 看 docling 容器日志
```

### 问题:Docling 处理很慢

- 首次请求 5-10 秒(模型 lazy 加载),后续 2-5 秒/页
- 大 PDF(>50 页)可能超时,看 `DOCLING_TIMEOUT` 配置(默认 300s)

---

## 📊 测试结果记录表

| 场景 | Docling 开启 | Docling 关闭 | 差异 |
|---|---|---|---|
| A. 导出 Markdown | 有标题+表格 | 纯文本 | ⭐⭐⭐⭐⭐ |
| B. AI 全文摘要 | 引用章节+表格数据 | 摘要较泛 | ⭐⭐⭐ |
| C. OCR 识别 | 结构化(文本型PDF) | 走 PaddleOCR | ⭐⭐ |

---

## 🔗 关联

- **Docling 架构**:[plans/2026-08-09-ocr-models-offline-deploy.md](2026-08-09-ocr-models-offline-deploy.md)
- **OCR 使用指南**:[plans/ocr-usage-guide.md](ocr-usage-guide.md)
- **修复记录**:AiStatusController.java:55 之前硬编码 `docling=false`,2026-08-09 修复为真实检查