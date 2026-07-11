package com.miaotong.doc.service.ai;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.service.DoclingService;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 统一文档内容提取服务
 * 所有 AI 功能共用，支持智能分块和结构化提取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentContentService {

    private final DocumentService documentService;
    private final StorageService storageService;
    private final DoclingService doclingService;

    private static final int MAX_CONTEXT_LENGTH = 12000;

    /**
     * 提取文档纯文本（自动选择最佳引擎）
     */
    public String extractText(Long documentId) {
        return extractText(documentId, MAX_CONTEXT_LENGTH);
    }

    /**
     * 提取文档纯文本（指定最大长度）
     */
    public String extractText(Long documentId, int maxLength) {
        Document doc = documentService.getDocument(documentId);
        try {
            byte[] bytes = storageService.load(doc.getFilePath());
            String text = extractTextByType(bytes, doc.getFileType());

            if (text.length() > maxLength) {
                text = text.substring(0, maxLength) + "\n...(内容过长，已截断)";
            }
            return text;
        } catch (Exception e) {
            log.warn("提取文档文本失败: docId={}", documentId, e);
            return "";
        }
    }

    /**
     * 提取文档结构化文本（使用 Docling，如果可用）
     */
    public String extractStructured(Long documentId) {
        try {
            // 优先使用 Docling（如果启用且可用）
            if (doclingService != null) {
                String structured = doclingService.parse(documentId);
                if (structured != null && !structured.isBlank()) {
                    if (structured.length() > MAX_CONTEXT_LENGTH) {
                        structured = structured.substring(0, MAX_CONTEXT_LENGTH) + "\n...(内容过长，已截断)";
                    }
                    return structured;
                }
            }
        } catch (Exception e) {
            log.warn("Docling 解析失败，回退到基础提取: docId={}", documentId, e);
        }
        // 回退到基础提取
        return extractText(documentId);
    }

    /**
     * 智能分块（用于长文档问答）
     */
    public List<String> smartChunk(String content, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        // 按段落分割
        String[] paragraphs = content.split("\\n\\s*\\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) continue;

            if (currentChunk.length() + paragraph.length() + 2 <= chunkSize) {
                currentChunk.append(paragraph).append("\n\n");
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
                // 如果段落太长，进一步分割
                if (paragraph.length() > chunkSize) {
                    String[] sentences = paragraph.split("(?<=[。！？.!?])\\s*");
                    StringBuilder subChunk = new StringBuilder();
                    for (String sentence : sentences) {
                        if (subChunk.length() + sentence.length() + 1 <= chunkSize) {
                            subChunk.append(sentence).append(" ");
                        } else {
                            if (subChunk.length() > 0) {
                                chunks.add(subChunk.toString().trim());
                            }
                            subChunk = new StringBuilder(sentence).append(" ");
                        }
                    }
                    currentChunk = subChunk;
                } else {
                    currentChunk = new StringBuilder(paragraph).append("\n\n");
                }
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        // 应用重叠
        if (overlap > 0 && chunks.size() > 1) {
            List<String> overlappedChunks = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                StringBuilder chunk = new StringBuilder(chunks.get(i));
                if (i > 0) {
                    String prevChunk = chunks.get(i - 1);
                    int overlapStart = Math.max(0, prevChunk.length() - overlap);
                    String overlapText = prevChunk.substring(overlapStart);
                    chunk.insert(0, overlapText).append("\n\n");
                }
                overlappedChunks.add(chunk.toString().trim());
            }
            return overlappedChunks;
        }

        return chunks;
    }

    /**
     * 选择与问题最相关的分块
     */
    public String selectRelevantChunks(List<String> chunks, String question, int maxChunks, int maxLength) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        if (chunks.size() <= maxChunks) {
            String result = String.join("\n\n", chunks);
            if (result.length() > maxLength) {
                result = result.substring(0, maxLength) + "\n...(内容过长，已截断)";
            }
            return result;
        }

        // 关键词匹配选择相关块
        String[] keywords = question.toLowerCase().split("[\\s,，。？?！!]+");
        List<ScoredChunk> scored = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkLower = chunks.get(i).toLowerCase();
            int score = 0;
            for (String keyword : keywords) {
                if (keyword.length() >= 2 && chunkLower.contains(keyword)) {
                    score++;
                }
            }
            scored.add(new ScoredChunk(i, score));
        }

        scored.sort((a, b) -> b.score - a.score);

        List<Integer> selectedIndices = new ArrayList<>();
        for (int i = 0; i < Math.min(maxChunks, scored.size()); i++) {
            selectedIndices.add(scored.get(i).index);
        }
        selectedIndices.sort(Integer::compareTo);

        StringBuilder result = new StringBuilder();
        for (int idx : selectedIndices) {
            result.append(chunks.get(idx)).append("\n\n");
        }

        String content = result.toString();
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength) + "\n...(内容过长，已截断)";
        }
        return content;
    }

    private record ScoredChunk(int index, int score) {}

    /**
     * 根据文件类型提取文本
     */
    private String extractTextByType(byte[] bytes, String fileType) {
        if ("pdf".equals(fileType)) {
            return extractTextFromPdf(bytes);
        } else if ("docx".equals(fileType)) {
            return extractTextFromDocx(bytes);
        } else {
            // md, txt, 其他文本格式
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * 从 PDF 提取文本
     */
    private String extractTextFromPdf(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdf);
            // 确保文本是有效的 UTF-8
            return sanitizeUtf8Text(text);
        } catch (Exception e) {
            log.warn("PDF 文本提取失败", e);
            return "";
        }
    }

    /**
     * 清理并确保 UTF-8 编码正确
     */
    private String sanitizeUtf8Text(String text) {
        if (text == null) return "";
        try {
            // 尝试重新编码以修复潜在的编码问题
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 如果失败，替换非法字符
            return text.replaceAll("[^\\x00-\\x7F\\x80-\\xBF\\xC0-\\xFF]", "?");
        }
    }

    /**
     * 从 DOCX 提取文本
     */
    private String extractTextFromDocx(byte[] bytes) {
        try {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return xml.replaceAll("<[^>]+>", " ")
                            .replaceAll("&amp;", "&")
                            .replaceAll("&lt;", "<")
                            .replaceAll("&gt;", ">")
                            .replaceAll("&quot;", "\"")
                            .replaceAll("\\s+", " ")
                            .trim();
                }
            }
        } catch (Exception e) {
            log.warn("DOCX 文本提取失败", e);
        }
        return "";
    }
}
