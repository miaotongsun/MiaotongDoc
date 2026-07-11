package com.miaotong.doc.service.ai;

/**
 * AI 提示词模板管理
 * 所有提示词集中管理，统一风格和质量
 */
public class PromptTemplates {

    // ===== 内容生成 =====
    public static final String GENERATE =
        "你是一个专业的写作助手。请根据用户的要求直接输出内容，不要添加任何前缀、解释或说明。\n\n" +
        "用户要求：{prompt}";

    // ===== 文档问答 =====
    public static final String DOCUMENT_QA =
        "你是一个专业的文档助手。请根据以下文档内容回答用户的问题。\n" +
        "文档可能是 PDF、Word、Markdown 或纯文本格式。请注意：\n" +
        "- 忽略页眉、页脚、页码等非正文内容\n" +
        "- 表格内容请综合理解，不要逐格回答\n" +
        "- 如果文档中没有相关信息，请如实说明，不要推测。\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===\n\n" +
        "用户问题：{question}";

    // ===== 多轮文档问答 =====
    public static final String DOCUMENT_QA_MULTI_TURN =
        "你是一个专业的文档助手。请根据文档内容和对话历史回答用户的问题。\n" +
        "规则：\n" +
        "- 引用文档内容时，请给出具体位置（如「第2段」「表格第3行」）\n" +
        "- 多轮对话中，后续问题可以引用前面对话中的结论\n" +
        "- 如果文档中没有相关信息，请如实说明，不要推测。\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===\n\n" +
        "对话历史：\n{history}\n\n" +
        "用户问题：{question}";

    // ===== 文档摘要 =====
    public static final String SUMMARIZE =
        "请对以下文档内容生成一个简洁的摘要，突出关键信息和要点。\n" +
        "使用与文档相同的语言。\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===";

    // ===== 结构化摘要 =====
    public static final String STRUCTURED_SUMMARIZE =
        "请对以下文档内容生成结构化摘要，包含以下五个部分：\n" +
        "1. 文档类型（如合同、报告、通知等）\n" +
        "2. 核心主题（一句话概括）\n" +
        "3. 关键要点（列出 3-5 个要点）\n" +
        "4. 重要数据（如有数字、日期、金额等）\n" +
        "5. 结论与建议\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===";

    // ===== 翻译 =====
    public static final String TRANSLATE =
        "请将以下文本翻译为{lang}。只输出翻译结果，不要添加任何解释或说明。\n" +
        "保留原文的段落格式和结构，专业术语保持准确。\n\n" +
        "=== 待翻译文本 ===\n{text}\n=== 文本结束 ===";

    // ===== 上下文感知翻译 =====
    public static final String CONTEXT_AWARE_TRANSLATE =
        "请将以下文本翻译为{lang}。参考上下文以确保专业术语翻译准确。\n" +
        "只输出翻译结果，不要添加任何解释。\n\n" +
        "=== 上下文 ===\n{context}\n=== 上下文结束 ===\n\n" +
        "=== 待翻译文本 ===\n{text}\n=== 文本结束 ===";

    // ===== 改写 =====
    public static final String REWRITE =
        "{instruction}。只输出改写后的文本，不要添加任何解释或说明。\n\n" +
        "=== 原文 ===\n{text}\n=== 原文结束 ===";

    // ===== 表格提取 =====
    public static final String EXTRACT_TABLES =
        "请从以下文档内容中提取所有表格数据。\n" +
        "以 JSON 数组格式输出，每个表格包含 \"headers\"（表头数组）和 \"rows\"（行数据二维数组）字段。\n" +
        "只输出 JSON，不要添加任何解释。\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===";

    // ===== PDF 表格问答 =====
    public static final String TABLE_QA =
        "你是一个文档助手。以下内容来自文档中的表格。\n" +
        "请根据表格内容准确回答问题。如果表格中没有相关信息，请如实说明。\n\n" +
        "=== 表格内容 ===\n{content}\n=== 表格结束 ===\n\n" +
        "用户问题：{question}";

    // ===== 文档对比 =====
    public static final String COMPARE_DOCUMENTS =
        "请对比以下两个文档版本，详细分析差异：\n" +
        "1. 新增内容（版本B中有但版本A中没有的）\n" +
        "2. 删除内容（版本A中有但版本B中没有的）\n" +
        "3. 修改内容（两个版本中都存在但有差异的）\n" +
        "4. 影响分析（这些变更可能带来的影响）\n\n" +
        "=== 版本A ===\n{doc1}\n=== 版本A结束 ===\n\n" +
        "=== 版本B ===\n{doc2}\n=== 版本B结束 ===";

    // ===== 合同审查 =====
    public static final String CONTRACT_REVIEW =
        "你是一个专业的合同审查助手。请对以下合同内容进行审查，分析：\n" +
        "1. 风险点（列出可能存在的法律或商业风险）\n" +
        "2. 关键条款（列出合同中的重要条款）\n" +
        "3. 缺失条款（列出可能缺少的重要条款）\n" +
        "4. 修改建议（针对风险点和缺失条款提出修改建议）\n" +
        "5. 总体评估（对合同的整体评价）\n\n" +
        "=== 合同内容 ===\n{content}\n=== 合同结束 ===";

    // ===== 智能标签 =====
    public static final String TAG_SUGGESTION =
        "请根据以下文档内容，生成 3-5 个最相关的标签（关键词）。\n" +
        "以 JSON 数组格式输出，只输出标签，不要添加解释。\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===";

    // ===== 智能分类 =====
    public static final String FOLDER_SUGGESTION =
        "请根据以下文档内容，判断它应该归类到哪个文件夹。\n" +
        "可选的文件夹列表：{folders}\n" +
        "输出格式：{\"folderId\": id, \"folderName\": \"名称\", \"reason\": \"原因\"}\n" +
        "如果都不合适，输出 {\"folderId\": null, \"folderName\": \"新建文件夹建议名\", \"reason\": \"原因\"}\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===";

    // ===== 续写 =====
    public static final String CONTINUE_WRITING =
        "请根据以下写作上下文继续创作，直接输出续写内容。\n" +
        "要求：\n" +
        "- 保持与原文相同的写作风格、语气和格式\n" +
        "- 续写内容应自然衔接上文，不重复已有内容\n" +
        "- 如需要新段落，以完整段落形式续写\n\n" +
        "=== 写作上下文 ===\n{context}\n=== 上下文结束 ===";

    // ===== 视觉问答 =====
    public static final String VISION_QA =
        "请根据图片内容回答用户的问题。如果图片中没有相关信息，请如实说明。";

    // ===== 关键词提取 =====
    public static final String EXTRACT_KEYWORDS =
        "请从以下文档中提取 5-10 个最重要的关键词或短语。\n" +
        "以 JSON 数组格式输出，只输出关键词，不要添加解释。\n\n" +
        "=== 文档内容 ===\n{content}\n=== 文档结束 ===";
}
