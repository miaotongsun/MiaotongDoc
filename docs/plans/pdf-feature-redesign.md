# 妙同文档 — PDF 功能重新规划

---

## 一、现状分析

### 当前 PDF 功能清单

| 功能 | 位置 | 状态 |
|------|------|------|
| PDF 查看器 (pdfjs-dist) | PdfViewer.vue | ✅ 已完成 |
| 页面导航 / 缩放 | PdfViewer.vue | ✅ 已完成 |
| 高亮批注 (5色) | PdfViewer.vue | ✅ 已完成 |
| 文字批注 (评论) | PdfViewer.vue | ✅ 已完成 |
| 手绘批注 | PdfViewer.vue | ✅ 已完成 |
| 实时协作批注 (Yjs) | PdfViewer.vue | ✅ 已完成 |
| 文本提取 (OCR) | PdfController.java | ✅ 已完成 |
| AI 对话 / 摘要 | PdfViewer.vue + DocumentAiController | ✅ 已完成 |
| Office→PDF 导出 | PdfExportService.java | ✅ 已完成 |
| 水印叠加 | PdfExportService.java | ✅ 已完成 |
| PDF 元数据查看 | PdfController.java | ✅ 已完成 |
| 空白 PDF 创建 | DocGenerator.java | ✅ 已完成 |

### 当前不足

1. **格式转换单一**：只支持 Office→PDF 单向导出，不支持 PDF→Word/Markdown/图片等
2. **无页面操作**：无法合并、拆分、旋转、重排页面
3. **无安全功能**：无法加密/解密 PDF、添加/移除密码
4. **无表单功能**：无法填写 PDF 表单、提取表单数据
5. **OCR 能力弱**：当前只是文本提取(PDFTextStripper)，不是真正的 OCR（扫描件无法识别）
6. **无压缩功能**：无法压缩 PDF 减小文件体积
7. **无批量操作**：所有操作都是单文件
8. **批注类型少**：缺少下划线、删除线、图章、签名等常见批注类型
9. **缺少页面预览缩略图**：无法直观看到每页内容概览
10. **缺少文本搜索**：无法在 PDF 中搜索文字

---

## 二、竞品功能参考

### Stirling PDF (⭐ 50K+, 开源 PDF 工具标杆)

| 分类 | 功能 |
|------|------|
| **页面操作** | 合并、拆分(按页/按大小/逐页)、旋转、裁剪、重排、提取指定页 |
| **格式转换** | PDF↔图片、PDF→Word、PDF→HTML、PDF→Markdown、PDF→XML、图片→PDF |
| **压缩优化** | 智能压缩、减小文件体积 |
| **安全加密** | 加密/解密、添加/移除密码、权限控制(打印/复制/编辑) |
| **元数据** | 查看/编辑/移除元数据 |
| **水印** | 文字水印、图片水印 |
| **OCR** | 基于 Tesseract 的光学字符识别 |
| **签名** | 电子签名、时间戳 |
| **表单** | 填写表单、扁平化表单、提取表单数据 |
| **页面编号** | 添加页码、页眉页脚 |
| **对比** | 两个 PDF 差异对比 |
| **修复** | 修复损坏的 PDF |

### PDF24 (免费 PDF 工具套件)

| 分类 | 功能 |
|------|------|
| **核心** | 合并、拆分、压缩、转换、编辑、注释、保护、解锁 |
| **特色** | PDF 切割器、PDF 比较器、PDF 扫描仪(OCR) |
| **编辑** | 文本编辑、图片插入、形状绘制、注释 |

### OnlyOffice (协作办公套件)

| 分类 | 功能 |
|------|------|
| **编辑** | 直接编辑 PDF 文本、插入图片、形状、表格 |
| **表单** | 创建/填写 PDF 表单 |
| **协作** | 实时多人编辑、评论、审阅 |
| **转换** | PDF↔DOCX 互转 |

---

## 三、新功能规划

### 功能架构总览

```
妙同PDF (MiaotongPDF)
│
├── 1. 查看与导航          ← 优化现有
│   ├── PDF 渲染查看       (已有)
│   ├── 页面缩略图         ★ 新增
│   ├── 文本搜索           ★ 新增
│   ├── 目录/书签导航      ★ 新增
│   └── 全屏/双页/单页模式  ★ 新增
│
├── 2. 格式转换            ← 重点新增
│   ├── PDF → Word (docx)  ★ 新增
│   ├── PDF → Markdown     ★ 新增
│   ├── PDF → 图片 (PNG/JPG) ★ 新增
│   ├── PDF → 纯文本       (已有)
│   ├── 图片 → PDF         ★ 新增
│   ├── Markdown → PDF     ★ 新增
│   └── Office → PDF       (已有)
│
├── 3. 页面操作            ← 全新模块
│   ├── 合并 PDF           ★ 新增
│   ├── 拆分 PDF           ★ 新增
│   ├── 旋转页面           ★ 新增
│   ├── 删除页面           ★ 新增
│   ├── 提取页面           ★ 新增
│   ├── 重排页面(拖拽)     ★ 新增
│   └── 裁剪页面           ★ 新增
│
├── 4. 批注与标注          ← 扩展现有
│   ├── 高亮               (已有)
│   ├── 评论               (已有)
│   ├── 手绘               (已有)
│   ├── 下划线             ★ 新增
│   ├── 删除线             ★ 新增
│   ├── 区域选择           ★ 新增
│   ├── 图章(已审批/机密等) ★ 新增
│   └── 电子签名           ★ 新增
│
├── 5. 安全与保护          ← 全新模块
│   ├── 添加密码           ★ 新增
│   ├── 移除密码(需原密码)  ★ 新增
│   ├── 权限控制           ★ 新增
│   │   (禁止打印/复制/编辑)
│   └── PDF/A 归档格式     ★ 新增
│
├── 6. 优化与处理          ← 全新模块
│   ├── 压缩 PDF           ★ 新增
│   ├── 添加水印           (已有,可扩展)
│   ├── 添加页码           ★ 新增
│   ├── 添加页眉页脚       ★ 新增
│   ├── 扁平化表单         ★ 新增
│   └── 修复损坏 PDF       ★ 新增
│
├── 7. OCR 识别            ← 增强现有
│   ├── 扫描件文字识别     ★ 增强
│   ├── 识别语言选择       ★ 新增
│   ├── 识别结果编辑       ★ 新增
│   └── 可搜索 PDF 生成    ★ 新增
│
├── 8. AI 智能             ← 优化现有
│   ├── AI 对话            (已有)
│   ├── AI 摘要            (已有)
│   ├── AI 翻译            ★ 新增
│   ├── AI 表格提取        ★ 新增
│   └── AI 关键信息提取    ★ 新增
│
└── 9. 批量操作            ← 全新模块
    ├── 批量转换           ★ 新增
    ├── 批量压缩           ★ 新增
    └── 批量添加水印       ★ 新增
```

---

## 四、分阶段实施计划

### 第一阶段：核心增强 (P0)

> 目标：补齐最基础的 PDF 工具能力，让用户能在平台内完成日常 PDF 处理

| 序号 | 功能 | 前端工作 | 后端工作 | 复杂度 |
|------|------|----------|----------|--------|
| 1 | **页面缩略图** | PdfViewer 左侧添加缩略图面板 | — | 低 |
| 2 | **文本搜索** | PdfViewer 添加搜索栏 + 高亮匹配 | — | 中 |
| 3 | **PDF→Word 转换** | 按钮+进度提示 | 接入 LibreOffice/OnlyOffice 转换 | 中 |
| 4 | **PDF→Markdown 转换** | 按钮+预览 | 基于 PDFBox 提取 + 格式化 | 中 |
| 5 | **PDF→图片 导出** | 按钮+格式选择 | pdfjs 前端渲染 + canvas 导出 | 低 |
| 6 | **图片→PDF** | 拖拽上传界面 | PDFBox 图片合成 PDF | 低 |
| 7 | **合并 PDF** | 拖拽排序界面 | PDFBox 合并 | 中 |
| 8 | **拆分 PDF** | 选择拆分方式界面 | PDFBox 拆分 | 中 |
| 9 | **旋转/删除页面** | 缩略图右键菜单 | PDFBox 页面操作 | 低 |
| 10 | **压缩 PDF** | 压缩级别选择+预览 | PDFBox 图片重采样+优化 | 中 |

### 第二阶段：安全与标注 (P1)

> 目标：满足企业级安全需求，丰富批注能力

| 序号 | 功能 | 前端工作 | 后端工作 | 复杂度 |
|------|------|----------|----------|--------|
| 11 | **PDF 加密/解密** | 密码输入对话框 | PDFBox 加密/解密 | 低 |
| 12 | **权限控制** | 权限配置面板 | PDFBox 权限设置 | 中 |
| 13 | **下划线/删除线** | PdfViewer 批注工具栏扩展 | — | 中 |
| 14 | **图章批注** | 预设图章 + 自定义图章 | — | 中 |
| 15 | **电子签名** | 签名板 + 签名位置选择 | 签名图片嵌入 PDF | 高 |
| 16 | **添加页码** | 位置/格式选择 | PDFBox 页码渲染 | 低 |
| 17 | **添加页眉页脚** | 内容/格式配置 | PDFBox 文本渲染 | 低 |
| 18 | **目录/书签** | 书签树面板 | PDFBox 书签读取 | 中 |

### 第三阶段：OCR 与 AI (P2)

> 目标：提升扫描件处理能力，发挥 AI 优势

| 序号 | 功能 | 前端工作 | 后端工作 | 复杂度 |
|------|------|----------|----------|--------|
| 19 | **真实 OCR (Tesseract)** | OCR 进度+结果展示 | Docker 集成 Tesseract | 高 |
| 20 | **多语言 OCR** | 语言选择下拉 | Tesseract 语言包 | 中 |
| 21 | **可搜索 PDF 生成** | 一键生成按钮 | OCR 文本层嵌入 PDF | 高 |
| 22 | **AI 翻译** | 翻译结果对照显示 | LLM API 调用 | 中 |
| 23 | **AI 表格提取** | 表格预览+导出 | LLM 结构化提取 | 中 |
| 24 | **批量操作** | 批量选择+进度 | 任务队列处理 | 高 |

---

## 五、技术选型确认

### 已确认的技术栈

| 能力 | 技术 | 许可 | 说明 |
|------|------|------|------|
| PDF 渲染 | pdfjs-dist | Apache 2.0 | 前端，已有 |
| PDF 处理 | Apache PDFBox 3.0.3 | Apache 2.0 | 后端，已有 |
| Office→PDF | OnlyOffice ConvertService | AGPL | 已有，继续使用 |
| AI 文档解析 | Docling (Docker) | MIT | 新增，IBM 开源 |
| OCR 识别 | Tesseract OCR | Apache 2.0 | 新增，Google 开源 |
| 实时协作 | Yjs + y-websocket | MIT | 已有 |
| 前端 UI | Element Plus + Vue 3 | MIT | 已有 |

### 不采用的方案

| 技术 | 原因 |
|------|------|
| Marker / Surya | GPL v3 许可，银行项目有合规风险 |
| Stirling PDF | AGPL v3，要求开源整个网络服务 |
| LibreOffice 替代 OnlyOffice | 不需要，OnlyOffice 继续使用 |

---

## 六、代码结构规划

### 后端新增文件

```
miaotongdoc-server/src/main/java/com/miaotong/doc/
│
├── controller/
│   └── PdfController.java              ← 已有(134行)，扩展新增接口
│       ├── GET  /{id}/text             (已有)
│       ├── GET  /{id}/info             (已有)
│       ├── GET  /{id}/pages/{num}/text (已有)
│       ├── POST /{id}/convert          ★ 格式转换
│       ├── POST /{id}/pages/rotate     ★ 旋转页面
│       ├── DELETE /{id}/pages/{num}    ★ 删除页面
│       ├── POST /{id}/pages/extract    ★ 提取页面
│       ├── POST /{id}/pages/reorder    ★ 重排页面
│       ├── POST /merge                 ★ 合并多个 PDF
│       ├── POST /{id}/split            ★ 拆分 PDF
│       ├── POST /{id}/compress         ★ 压缩
│       ├── POST /{id}/encrypt          ★ 加密
│       ├── POST /{id}/decrypt          ★ 解密
│       └── POST /{id}/ocr              ★ OCR 识别
│
├── service/
│   ├── PdfExportService.java           ← 已有，不变(Office→PDF+水印)
│   ├── PdfToolService.java             ★ 新增：所有 PDF 工具操作
│   │   ├── convert()                   格式转换(PDF→Word/MD/图片)
│   │   ├── merge()                     合并多个 PDF
│   │   ├── split()                     拆分 PDF
│   │   ├── rotatePages()              旋转页面
│   │   ├── deletePages()              删除页面
│   │   ├── extractPages()             提取页面
│   │   ├── reorderPages()             重排页面
│   │   ├── compress()                 压缩优化
│   │   ├── encrypt()                  添加密码
│   │   ├── decrypt()                  移除密码
│   │   └── ocr()                      调用 Tesseract
│   └── DoclingService.java             ★ 新增：Docling 微服务调用
│       └── parse()                     PDF→结构化 Markdown/JSON
│
├── entity/
│   └── PdfTask.java                    ★ 新增：异步任务(对应 mt_pdf_task)
│
├── repository/
│   └── PdfTaskRepository.java          ★ 新增
│
├── dto/
│   ├── PdfConvertRequest.java          ★ 转换请求(目标格式)
│   ├── PdfPageOperationRequest.java    ★ 页面操作(页码列表+操作类型)
│   ├── PdfMergeRequest.java            ★ 合并请求(文档ID列表)
│   ├── PdfSplitRequest.java            ★ 拆分请求(方式+范围)
│   └── PdfTaskResponse.java            ★ 任务状态响应
│
└── config/
    └── DoclingProperties.java          ★ Docling 服务地址配置
```

### 后端不新增的文件

| 文件 | 理由 |
|------|------|
| PdfAnnotation.java | mt_pdf_annotation 表已有，JPA 实体可后续需要时再加 |
| PdfSecurityController.java | 加密/解密接口放 PdfController 即可，2-3 个方法 |
| PdfCompressService.java | 压缩逻辑简单，放 PdfToolService 即可 |
| PdfSecurityService.java | 加密逻辑简单，放 PdfToolService 即可 |

### 前端新增文件

```
miaotongdoc-web/src/
│
├── api/
│   └── pdf.ts                          ★ 新增：PDF 工具 API 模块
│
├── components/
│   ├── PdfViewer.vue                   ← 已有，扩展搜索+缩略图
│   └── pdf/                            ★ 新增子目录
│       ├── PdfThumbnailPanel.vue       ★ 左侧缩略图面板
│       ├── PdfSearchBar.vue            ★ 顶部搜索栏
│       ├── PdfAnnotationToolbar.vue    ★ 批注工具栏(从 PdfViewer 拆出)
│       ├── PdfConvertDialog.vue        ★ 格式转换对话框
│       ├── PdfMergeDialog.vue          ★ 合并对话框
│       ├── PdfSplitDialog.vue          ★ 拆分对话框
│       ├── PdfPageManager.vue          ★ 页面管理(拖拽排序)
│       └── PdfSignaturePad.vue         ★ 签名板
│
└── views/
    └── DocEditor.vue                   ← 已有，无需改动
```

### 文件增减汇总

| 类型 | 已有(改动) | 新增 | 合计 |
|------|-----------|------|------|
| Controller | 1 扩展 | 0 | 1 |
| Service | 0 | 2 | 2 |
| Entity | 0 | 1 | 1 |
| Repository | 0 | 1 | 1 |
| DTO | 0 | 5 | 5 |
| Config | 0 | 1 | 1 |
| SQL | 0 | 1 | 1 |
| 前端 API | 0 | 1 | 1 |
| 前端组件 | 1 改动 | 8 | 9 |
| **合计** | **2** | **20** | **22** |

---

## 七、数据库变更

```sql
-- V22__add_pdf_task.sql
CREATE TABLE mt_pdf_task (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES mt_document(id),
    task_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    parameters JSONB,
    result_document_id BIGINT,
    result_file_path VARCHAR(500),
    error_message TEXT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_pdf_task_status ON mt_pdf_task(status);
CREATE INDEX idx_pdf_task_doc ON mt_pdf_task(document_id);
```

---

## 八、分阶段实施计划

| 阶段 | 功能数 | 预计工期 | 核心价值 |
|------|--------|----------|----------|
| 第一阶段 | 10 | 3-4 周 | 格式转换+页面操作，覆盖 80% 日常需求 |
| 第二阶段 | 8 | 2-3 周 | 企业安全+丰富批注，满足合规要求 |
| 第三阶段 | 6 | 2-3 周 | OCR+AI 增强，差异化竞争力 |

**第一阶段完成后**，妙同PDF 将具备与 Stirling PDF 相当的基础工具能力，同时保持已有的协作编辑和 AI 优势，形成"工具+协作+AI"三位一体的 PDF 解决方案。

### 技术栈一句话总结

> **PDFBox 管工具，OnlyOffice 管转换，Docling 管 AI 理解，pdfjs-dist 管渲染。各司其职。**

### 开源许可合规

所有新增技术均为 Apache 2.0 / MIT / MPL 2.0 许可，银行商用无风险。不引入 GPL/AGPL 组件。
