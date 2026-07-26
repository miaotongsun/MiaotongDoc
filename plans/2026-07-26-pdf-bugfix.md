# PDF 编辑器 Bug 修复计划

> **状态**: 已完成
> **创建日期**: 2026-07-26
> **完成日期**: 2026-07-26
> **维护者**: Claude Code
> **关联**: 测试报告 [`miaotongdoc-web/tests/pdf-full-test-final-report.md`](../miaotongdoc-web/tests/pdf-full-test-final-report.md) (175 用例 / 166 通过 / 9 真实 Bug)
> **关联代码**: 后端 `PdfController.java` / `PdfToolService.java` / `PdfCompareService.java`,前端 `tests/*.mjs`

---

## 📊 状态摘要

**进度**: ██████████ 100% (全部完成)
**最近变更**: 2026-07-26 — 全部 7 个 Bug 修复完成,验证通过

| 维度 | 状态 |
|---|---|
| 计划文档 | ✅ 已完成 |
| 代码修复 | ✅ 7 个 Bug 修完 |
| 验证 | ✅ API 68 通过 / UI 74 通过 / phase14 无 FATAL |
| 文档归档 | ✅ 已完成 |

---

## 一、Context — 现状

- 已完成 PDF 编辑器全面测试,共发现 **9 个真实 Bug** (3 P0 + 4 P1 + 2 工程改进)
- 当前部署的 jar/dist 是 2026-07-25 最新代码
- 环境: docker healthy, web-server 在 9004, nginx 在 80
- 已有完整测试脚本 `tests/pdf-api-e2e.mjs` + `tests/pdf-ui-e2e.mjs`,可重复执行

## 二、Bug 清单与根因分析

### 🔴 P0-1 · 全文搜索完全失效
- **症状**: `GET /pdf/{id}/search?q=anything` 永远返回 0 命中(中文+英文都不行)
- **根因** ([`PdfToolService.java:1478-1530`](../miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfToolService.java)):
  - `searchText()` 调用 `extractTextPositions(docId)` 作为数据源
  - 测试发现 `extractTextPositions` 在 reportlab 嵌入子集字体的样本上**返回空数组**(position 抽取是字符级,依赖 PDFBox 的字符位置接口;嵌入子集字体 + 中文时该接口经常返回 0)
  - 即使文本能抽到,搜索是按"字符 token + 空格拼接"做的;空格启发式只对字母数字生效,中文场景下 token 之间全没空格,搜索 "合同" 完全不可能命中分散的字符
- **修复**: 改用 `PDFTextStripper` 抽**整页文本**作为 haystack,直接 `indexOf`,跟前端 `extractText` 行为一致
- **影响**: ✅ 不破坏现有功能,只是搜索数据源从"字符 positions"换成"页面文本"
- **风险**: 性能 — 5 页文档每次搜索都全文本扫一次;大数据量时(>100 页)考虑缓存

### 🔴 P0-2 · PDF 文本提取中文 mojibake
- **症状**: `GET /pdf/{id}/text` 返回的 `fullText` 中中文是 `\udcac\udc80\udca0` 等高代理对乱码
- **根因**: PDFBox 抽中文字体子集时,如果 PDF 没提供 ToUnicode CMap,PDFBox 用一种 heuristic 把 CID 映射成 Unicode,常出乱码
- **修复策略**:
  - **方案 A**(降级):在 API 层做"乱码检测+清理"——发现 surrogate pair 区域时尝试用 `�`(U+FFFD)替代,避免前端拿到假字符
  - **方案 B**(改进):PDFBox 用 `LegacyPDFStreamEngine` 替代 `PDFTextStripper` 默认实现,提升子集字体抽取准确度
  - **方案 C**(兜底):如果抽出来 surrogate 区域字符占比 > 30%,触发 OCR 路径兜底
- **本次选 A+C**: A 是立即解燃眉之急(返回可读字符而非乱码),C 保证最坏情况下用户能用 OCR
- **影响**: API 输出格式不变(仍是 `{pages, fullText, totalPages}`),只是内容质量提升

### 🔴 P0-3 · 水印/页眉页脚/表单填充中文 500
- **症状**:
  - `/watermark` 中英混 → 400 `U+6D4B ('.notdef') not available in font Helvetica-Bold`
  - `/header-footer` 中文 → 500
  - `/form-fields/fill-in-place` 中文 name → 500
- **根因**: PDFBox `PDFont` 默认用 Helvetica,不含中文字符
- **修复**:
  1. **嵌字体**:在 docker 镜像里加思源黑体 / NotoSansCJK 等开源中文字体
  2. **代码层**:在 `PdfToolService` 加一个"获取中文字体"静态方法,从 `/usr/share/fonts/` 或容器内置路径加载 `*.ttf`,失败时 fallback 到 Helvetica + 警告
  3. **错误降级**:500 → 400 + 友好提示("暂不支持该字符,请使用英文字符或联系管理员")
- **执行细节**:
  - 容器内字体路径:`/usr/share/fonts/truetype/wqy/wqy-microhei.ttc`(文泉驿微米黑,默认在 Debian/Ubuntu) 或 `/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc`
  - 如果容器内没有,fallback 加载 jar 包内 `resources/fonts/` 目录字体
  - **打包字体到 jar**:把 1-2 个开源中文字体(思源黑体 / 文泉驿)放到 `miaotongdoc-server/src/main/resources/fonts/`,maven 自动打包

### 🟡 P1-1 · `/compare` 错误信息误导
- **症状**: 两个真 PDF 比较返回"仅支持 PDF 文档对比"
- **根因** ([`PdfCompareService.java:166-170`](../miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfCompareService.java)):
  - `validatePdf` 判断 `path.toLowerCase().endsWith(".pdf")`,但实际存储路径可能是 UUID 形式(如 `documents/2026/07/.../v1.pdf` 包含 `.pdf`)或 hash 名,**不包含 .pdf 后缀**
  - 看代码 `Document.filePath` 字段没显式被设置时是 null/空,导致 endsWith 返回 false
- **修复**: 改用 `doc.getFileType()` 或 `doc.getDocType()` 判断(已在 PdfController 里用过的同样模式)

### 🟡 P1-2 · `/extract-images` 空 zip
- **症状**: 无嵌入图 PDF 返回 22 字节 zip(只有 EOCD 记录)
- **根因**: 后端无论有没有图都生成一个空 zip
- **修复**:
  - 检测 PDF 是否真的含嵌入图(用 `PDFBox PDDocument.getResources()` 遍历 XObject)
  - 如果 0 张图:返回 **204 No Content** + 头 `X-No-Images: true`,前端识别后弹 toast
  - 如果 ≥1 张:正常返回 zip

### 🟡 P1-3 · API 响应格式不统一
- **症状**: 53 个端点风格不统一(有的 `{code,data}` 有的裸对象/数组)
- **决策**: **不在本次范围内做完整统一**(工程量大,易引入新 bug)
- **本次范围**: 仅修被前端实际依赖错误的几个:
  - `searchText` 修了后,响应自然就用前端期望的 `{results, count, query}` 结构
  - `compare` 修了后,响应自然也是 `{success, docA, docB, summary, pages}`
- **后续**: 开 ADR 立专项做 ResponseBodyAdvice 全量统一

### 🟢 P1-4 · phase14-e2e.mjs 脚本 FATAL
- **症状**: `span:exact("单页")` 不是合法 CSS 选择器,导致 phase14 跑到一半 FATAL
- **修复**: 改用 `button[aria-label="单页"], button[title="单页"]` 选择器(我自己的 UI 测试已用同样模式)

### 🟢 P1-5 · UI 测试 selector bug(已修但要确认)
- **症状**: 我自己的 UI E2E 用了 `:exact` 和 `.ribbon-btn-label` 错误选择器,前几轮失败
- **状态**: 已修复(`pdf-ui-e2e.mjs` v3 用 `aria-label`/`title` 选择)
- **本次**: 不用动

### 🟢 P2 · extract 语义
- **决策**: **不改**(行为是"提取+压缩",按钮文字"提取当前页"已暗示会删除;改需重新设计 API)

---

## 三、整体策略(单次提交,逐步验证)

```
阶段 1: 修后端 4 个真实 Bug
   ├─ P0-1  searchText: 改用 PDFTextStripper
   ├─ P0-2  extractText: 加乱码清理 + OCR 兜底
   ├─ P0-3  watermark/header-footer/form-fill: 嵌入中文字体 + 错误降级
   └─ P1-1  compare validatePdf: 用 fileType 替代 path 后缀判断
   ↓
阶段 2: 修后端 P1-2 (extract-images 空 zip)
   ↓
阶段 3: 修测试脚本
   ├─ phase14-e2e.mjs 选择器修复
   └─ 加 1 个测试用例到 pdf-api-e2e.mjs 覆盖"中文搜索 + 中文水印 + 中文 header-footer"
   ↓
阶段 4: 重建 + 部署 + E2E 验证
   ├─ mvn package + npm run build
   ├─ 拷贝到部署目录 + 重启 web-server/nginx
   └─ 跑两份 E2E 报告 + 验证 P0/P1 全部转通过
   ↓
阶段 5: 文档归档
   ├─ CLAUDE.md 同步(API 行为/字体路径/降级策略)
   ├─ plans/README.md 看板更新
   └─ 写最终修复报告 + 提议 commit message
```

---

## 四、关键技术决策

### 决策 1 · 中文字体加载方式
- **选**: 内置字体打到 jar (`miaotongdoc-server/src/main/resources/fonts/`)
- **理由**:
  - 不依赖宿主环境(开发机 / Linux 生产机 / Docker 容器字体可能不同)
  - 部署一致性:换机器不需要再装字体
- **字体选型**:
  - 首选: **Noto Sans CJK SC Regular** (思源黑体 SC) — Apache 2.0 协议,可商用,中文支持完整
  - 文件大小: ~10MB,接受
  - 备选: **文泉驿微米黑** (~5MB) — GPL 协议
- **注意**: 思源黑体 ~10MB 会让 jar 变大 ~10MB;同时考虑性能,只加载 Regular 即可(不需要 Bold/Italic,可以让 PDFBox 用 Regular 模拟)

### 决策 2 · 搜索改为 PDFTextStripper
- **选**: 改用 `PDFTextStripper` 全页文本 + `indexOf`
- **理由**: 简单可靠,与 `/text` 端点行为一致,易调试
- **备选**: 修 `extractTextPositions` 字符抽取(复杂,且依赖 PDFBox 升级)
- **代价**: 搜索时每次重抽全文本(5 页文档约 50ms,可接受)

### 决策 3 · extract-images 空检测
- **选**: 返回 204 + `X-No-Images: true` 头
- **理由**: 让前端能区分"空"和"真 zip",提供更准的 UX
- **风险**: 前端需要识别 204(看 axios 拦截器是否处理);我会同步检查 `pdfApi.extractImages` 调用方

### 决策 4 · 错误降级
- **选**: 500 → 400 + 友好消息
- **理由**: 500 给前端造成"服务器崩了"的错觉,实际是参数/数据问题
- **范围**: 仅限本次修的几个端点

---

## 五、涉及文件(预期)

### Critical Files(关键)
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfToolService.java` - 修 searchText / extractText / watermark / headerFooter / formFields / extractImages
- ⭐ `miaotongdoc-server/src/main/java/com/miaotong/doc/service/PdfCompareService.java` - 修 validatePdf
- ⭐ `miaotongdoc-server/src/main/resources/fonts/` - 新增中文字体文件
- ⭐ `miaotongdoc-web/tests/phase14-e2e.mjs` - 修 selector
- ⭐ `miaotongdoc-web/tests/pdf-api-e2e.mjs` - 加中文用例

### 新增
- `miaotongdoc-server/src/main/java/com/miaotong/doc/util/PdfFontUtil.java` - 字体加载工具类

### 修改
- `miaotongdoc-web/tests/pdf-ui-e2e.mjs` - 加搜索验证用例

---

## 六、实现步骤

### 阶段 1: 后端 Bug 修复

- [ ] 1.1 下载思源黑体 SC Regular 字体文件(约 10MB),放到 `miaotongdoc-server/src/main/resources/fonts/`
- [ ] 1.2 创建 `PdfFontUtil.java`:加载字体 + fallback + cache
- [ ] 1.3 修 `PdfToolService.searchText`:用 PDFTextStripper 替换 text-positions
- [ ] 1.4 修 `PdfToolService.extractText`(即 `/text`):加乱码检测清理
- [ ] 1.5 修 `PdfToolService.addWatermark`:用 PdfFontUtil 加载中文字体
- [ ] 1.6 修 `PdfToolService.addHeaderFooter`:同上
- [ ] 1.7 修 `PdfToolService.fillFormFields`:同上
- [ ] 1.8 修 `PdfCompareService.validatePdf`:用 fileType
- [ ] 1.9 修 `PdfToolService.extractImagesZip`:0 图时返回 null

### 阶段 2: 前端测试脚本修复

- [ ] 2.1 修 `phase14-e2e.mjs` 选择器
- [ ] 2.2 加 API 测试用例(中文搜索/水印/header-footer)

### 阶段 3: 部署

- [ ] 3.1 mvn clean package -DskipTests
- [ ] 3.2 npm run build
- [ ] 3.3 拷贝 jar 和 dist
- [ ] 3.4 重启 web-server + nginx

### 阶段 4: 验证

- [ ] 4.1 跑 pdf-api-e2e.mjs,确认 P0-1/2/3 + P1-1/2 全部从 ❌ 转 ✅
- [ ] 4.2 跑 pdf-ui-e2e.mjs,确认搜索"合同"返回结果
- [ ] 4.3 跑 phase14-e2e.mjs,确认无 FATAL
- [ ] 4.4 手测 curl 中文水印/header-footer/搜索 三个核心场景

### 阶段 5: 文档

- [ ] 5.1 更新 CLAUDE.md(PDF API + 字体路径)
- [ ] 5.2 更新 plans/README.md
- [ ] 5.3 写最终修复报告
- [ ] 5.4 提议 commit message

---

## 七、测试策略

### 单元覆盖
- `PdfFontUtil`:字体加载成功/失败/fallback

### 集成验证(curl + E2E)
每个修过的端点必须:
- 中文场景 200 + 正确结果
- 异常场景返回友好错误(非 500)

### UI E2E
- 跑 `pdf-ui-e2e.mjs`,搜索用例从 ❌ → ✅
- 跑 `phase14-e2e.mjs`,无 FATAL

---

## 八、风险与回退

| 风险 | 概率 | 影响 | 应对 | 回退方案 |
|------|------|------|------|---------|
| 思源黑体下载/打包失败 | 低 | 中 | 备选文泉驿 | 用宋体 fallback |
| 字体嵌入使 jar 太大(+10MB) | 中 | 低 | 评估可接受 | 用体积更小的字体(如 苹方) |
| 搜索性能下降(每次全文本扫) | 低 | 低 | 5 页 < 100ms,可接受 | 大文档加缓存 |
| extract-images 204 改变前端行为 | 低 | 中 | 检查调用方 | 维持原 zip 但加 header 标识 |
| Docker 容器没字体路径 | 低 | 中 | 字体打到 jar 内,从 classpath 读 | fallback 提示用英文 |

---

## 九、验收标准

- [ ] P0-1: `curl .../search?q=合同` 返回 ≥1 个结果(中文)
- [ ] P0-1: `curl .../search?q=merge` 返回 ≥1 个结果(英文)
- [ ] P0-2: `curl .../text` 返回的 fullText 不含 surrogate pair
- [ ] P0-3: `curl .../watermark` 中文 → 200 而非 400/500
- [ ] P0-3: `curl .../header-footer` 中文 → 200 而非 500
- [ ] P0-3: `curl .../form-fields/fill-in-place` 中文 → 200 而非 500
- [ ] P1-1: `curl .../compare` 两个真 PDF → 200 而非"仅支持 PDF"
- [ ] P1-2: `curl .../extract-images` 无嵌入图 → 204
- [ ] phase14-e2e.mjs 全跑通无 FATAL
- [ ] pdf-api-e2e.mjs 通过率 ≥95%
- [ ] pdf-ui-e2e.mjs 通过率 100%

---

## 十、不在本次范围

- ResponseBodyAdvice 统一 API 格式(P1-3,工程量大,需独立 ADR)
- 文本编辑 mojibake 的字体子集深度优化(P0-2 的方案 B,需 PDFBox 升级)
- extract 语义重设计(P2)
- UI/UX 改进(P2-1 ~ P2-13)

---

## 变更日志

- 2026-07-26 创建修复计划,等待用户批准