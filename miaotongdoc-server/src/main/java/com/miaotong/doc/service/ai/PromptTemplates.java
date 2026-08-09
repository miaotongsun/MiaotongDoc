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

    // ===== 合同付款计划抽取（2026-08-09 新增） =====
    public static final String CONTRACT_PAYMENT_EXTRACT =
        "你是一个专业的合同付款条款抽取助手。请从以下合同文本中抽取所有付款计划项,并以严格 JSON 数组格式输出。\n\n" +
        "【字段定义(每个元素)】\n" +
        "- title: 付款阶段标题(如:首付款 / 二期款 / 尾款 / 月供 / 验收款)\n" +
        "- amount: 本期金额(数字,单位元)\n" +
        "- currency: 币种(默认 CNY)\n" +
        "- dueDate: 应付款日期(ISO 8601 格式 YYYY-MM-DD;若合同只写\"签订后30日内\"等相对描述,请基于合同签订日期推算;若无法推算则填 null)\n" +
        "- remarks: 备注(付款条件、付款方式等,字符串,无则 null)\n\n" +
        "【输出格式要求】\n" +
        "1. 严格输出 JSON 数组,每个元素代表一期付款,不要包含外层对象\n" +
        "2. 金额转换为数字(不带\"元/万元\"等单位字符)\n" +
        "3. 日期统一为 YYYY-MM-DD\n" +
        "4. 仅基于提供的合同文本,不要编造;找不到的字段填 null\n" +
        "5. 若合同未明确付款计划,输出空数组 []\n\n" +
        "示例输出:[{\"title\":\"首付款\",\"amount\":30000,\"currency\":\"CNY\",\"dueDate\":\"2026-09-01\",\"remarks\":\"合同签订后7日内\"}]\n\n" +
        "=== 合同文本 ===\n{content}\n=== 合同结束 ===";

    // ===== 合同结构化字段抽取（PDF 路径用，2026-08-09 新增） =====
    public static final String CONTRACT_PARSE =
        "你是一个专业的合同信息抽取助手。请从以下合同文本中抽取关键字段，并以严格 JSON 格式输出。\n\n" +
        "【字段定义】\n" +
        "- contractNo: 合同编号（字符串，找不到填 null）\n" +
        "- contractType: 合同类型，从以下枚举选一个: purchase(采购)、sale(销售)、lease(租赁)、service(服务)、labor(劳务)、construction(工程)、other(其他)\n" +
        "- partyA: 甲方名称（字符串）\n" +
        "- partyB: 乙方名称（字符串）\n" +
        "- amount: 合同金额（数字，单位元；找不到填 null）\n" +
        "- signingDate: 签订日期（ISO 8601 格式 YYYY-MM-DD，找不到填 null）\n" +
        "- effectiveDate: 生效日期（YYYY-MM-DD）\n" +
        "- expiryDate: 到期日期（YYYY-MM-DD）\n\n" +
        "【输出格式要求】\n" +
        "1. 严格输出 JSON 对象，不要任何解释文字\n" +
        "2. 金额转换为数字（不带\"元/万元\"等单位字符）\n" +
        "3. 日期统一为 YYYY-MM-DD\n" +
        "4. 仅基于提供的合同文本，不要编造\n\n" +
        "=== 合同文本 ===\n{content}\n=== 合同结束 ===";

    // ===== 合同审查（结构化 JSON 输出，2026-08-09 重写） =====
    public static final String CONTRACT_REVIEW =
        "你是一个专业的合同审查助手。请对以下合同内容进行法律和商业风险审查，并以严格 JSON 格式输出审查结果。\n\n" +
        "【输出字段】\n" +
        "{\n" +
        "  \"riskLevel\": \"low|medium|high\",         // 整体风险等级\n" +
        "  \"riskScore\": 0-100,                        // 风险评分(0=无风险,100=极高风险)\n" +
        "  \"riskItems\": [                            // 风险项列表\n" +
        "    {\n" +
        "      \"category\": \"风险类别(如:违约责任/付款条款/知识产权/保密条款/争议解决/合同期限)\",\n" +
        "      \"description\": \"具体风险描述\",\n" +
        "      \"severity\": \"low|medium|high\"         // 该风险项的严重程度\n" +
        "    }\n" +
        "  ],\n" +
        "  \"keyClauses\": [                           // 关键条款\n" +
        "    {\n" +
        "      \"title\": \"条款标题\",\n" +
        "      \"summary\": \"条款摘要(1-2 句)\"\n" +
        "    }\n" +
        "  ],\n" +
        "  \"missingClauses\": [                       // 缺失的重要条款(纯字符串数组)\n" +
        "    \"缺失的条款名称\"\n" +
        "  ],\n" +
        "  \"suggestions\": [                          // 修改建议(纯字符串数组)\n" +
        "    \"建议内容\"\n" +
        "  ],\n" +
        "  \"summary\": \"对合同的整体评价(一段话,50-150 字)\"\n" +
        "}\n\n" +
        "【规则】\n" +
        "1. 严格输出 JSON,不要任何解释文字\n" +
        "2. 风险等级判定标准: low(<3 个风险项且无 high) / medium(3-5 个风险项或有 1 个 high) / high(>5 个风险项或 >2 个 high)\n" +
        "3. 风险评分参考风险等级: low=0-30, medium=31-60, high=61-100\n" +
        "4. 关键条款至少识别 3 条,缺失条款至少识别 2 条(如果都没问题,空数组即可)\n" +
        "5. 仅基于提供的合同文本,不要编造\n\n" +
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
