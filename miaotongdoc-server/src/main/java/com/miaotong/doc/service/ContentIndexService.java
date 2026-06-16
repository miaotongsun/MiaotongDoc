package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.entity.DocumentIndex;
import com.miaotong.doc.repository.DocumentRepository;
import com.miaotong.doc.repository.DocumentSearchRepository;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIndexService {

    private final DocumentSearchRepository documentSearchRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;

    /**
     * 索引单个文档到 Elasticsearch
     */
    public void indexDocument(Long documentId) {
        try {
            Document doc = documentRepository.findById(documentId).orElse(null);
            if (doc == null || doc.getIsDeleted()) return;

            byte[] fileBytes = storageService.load(doc.getFilePath());
            String text = extractText(fileBytes, doc.getFileType());

            DocumentIndex index = new DocumentIndex();
            index.setId(String.valueOf(documentId));
            index.setDocumentId(documentId);
            index.setTitle(doc.getTitle());
            index.setContent(text);
            index.setDocType(doc.getDocType());
            index.setFileType(doc.getFileType());
            index.setOwnerUserId(doc.getOwnerUserId());
            index.setDepartmentId(doc.getDepartmentId());
            index.setCreatedAt(doc.getCreatedAt());
            index.setUpdatedAt(doc.getUpdatedAt());

            // 获取创建人姓名
            userRepository.findById(doc.getOwnerUserId()).ifPresent(user -> {
                index.setOwnerName(user.getRealName() != null ? user.getRealName() : user.getUsername());
            });

            documentSearchRepository.save(index);
            log.debug("文档已索引到 ES: docId={}, title={}", documentId, doc.getTitle());
        } catch (Exception e) {
            log.warn("文档索引失败: docId={}, error={}", documentId, e.getMessage());
        }
    }

    /**
     * 删除文档索引
     */
    public void removeDocument(Long documentId) {
        try {
            documentSearchRepository.deleteById(String.valueOf(documentId));
            log.debug("文档索引已删除: docId={}", documentId);
        } catch (Exception e) {
            log.warn("文档索引删除失败: docId={}, error={}", documentId, e.getMessage());
        }
    }

    /**
     * 全文搜索文档（标题 + 内容）
     */
    public List<Long> searchContent(String keyword) {
        // 使用 Elasticsearch 搜索
        List<DocumentIndex> results = documentSearchRepository.searchByTitleOrContent(keyword, keyword);
        return results.stream()
                .map(DocumentIndex::getDocumentId)
                .collect(Collectors.toList());
    }

    /**
     * 获取文档内容中包含关键词的片段，关键词用 <mark> 标签高亮
     */
    public String getContentSnippet(Long documentId, String keyword) {
        try {
            DocumentIndex doc = documentSearchRepository.findById(String.valueOf(documentId)).orElse(null);
            if (doc == null || doc.getContent() == null) return "";

            String content = doc.getContent();
            if (content.isEmpty()) return "";

            String lowerContent = content.toLowerCase();
            String lowerKeyword = keyword.toLowerCase();
            int idx = lowerContent.indexOf(lowerKeyword);

            if (idx == -1) {
                // 如果找不到关键词，返回开头片段
                int end = Math.min(content.length(), 100);
                return content.substring(0, end).trim() + "...";
            }

            // 关键词前面保留 20 字符，后面保留 20 字符
            int start = Math.max(0, idx - 20);
            int end = Math.min(content.length(), idx + keyword.length() + 20);
            String before = content.substring(start, idx);
            String match = content.substring(idx, idx + keyword.length());
            String after = content.substring(idx + keyword.length(), end);

            StringBuilder snippet = new StringBuilder();
            if (start > 0) snippet.append("...");
            snippet.append(before);
            snippet.append("<mark>").append(match).append("</mark>");
            snippet.append(after);
            if (end < content.length()) snippet.append("...");

            return snippet.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从文档文件中提取文本
     */
    private String extractText(byte[] fileBytes, String fileType) {
        try {
            if ("docx".equals(fileType) || "xlsx".equals(fileType) || "pptx".equals(fileType)) {
                return extractTextFromZip(fileBytes);
            }
            if ("pdf".equals(fileType)) {
                return extractTextFromPdf(fileBytes);
            }
            // md 和其他文本格式直接读取
            return new String(fileBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("文本提取失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 从 PDF 文件中提取文本
     */
    private String extractTextFromPdf(byte[] fileBytes) {
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(fileBytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(pdf);
        } catch (Exception e) {
            log.warn("PDF 文本提取失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 从 docx/xlsx/pptx 文件中提取文本
     */
    private String extractTextFromZip(byte[] fileBytes) {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".xml") && !name.startsWith("[Content_Types]") && !name.startsWith("_rels")) {
                    byte[] content = zis.readAllBytes();
                    String xml = new String(content, StandardCharsets.UTF_8);
                    // 解码 XML 实体
                    xml = xml.replace("&amp;", "&")
                             .replace("&lt;", "<")
                             .replace("&gt;", ">")
                             .replace("&quot;", "\"")
                             .replace("&apos;", "'");
                    // 移除 XML 标签，保留文本（包括特殊字符）
                    String text = xml.replaceAll("<[^>]+>", " ")
                             .replaceAll("\\s+", " ")
                             .trim();
                    if (!text.isEmpty() && text.length() > 2) {
                        sb.append(text).append(" ");
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.warn("ZIP 文本提取失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    /**
     * 定时索引新文档或更新的文档
     */
    @Scheduled(cron = "0 */30 * * * ?")  // 每30分钟执行一次
    public void indexNewDocuments() {
        log.info("开始 Elasticsearch 文档索引任务");
        try {
            List<Document> docs = documentRepository.findByIsDeletedFalse(
                    org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
            int indexed = 0;
            for (Document doc : docs) {
                try {
                    documentSearchRepository.findById(String.valueOf(doc.getId())).ifPresentOrElse(
                            existing -> {
                                if (existing.getUpdatedAt() == null ||
                                        existing.getUpdatedAt().isBefore(doc.getUpdatedAt())) {
                                    indexDocument(doc.getId());
                                }
                            },
                            () -> indexDocument(doc.getId())
                    );
                    indexed++;
                } catch (Exception e) {
                    log.warn("索引文档失败: docId={}", doc.getId());
                }
            }
            log.info("Elasticsearch 文档索引完成: 处理{}篇文档", indexed);
        } catch (Exception e) {
            log.error("Elasticsearch 索引任务失败", e);
        }
    }
}
