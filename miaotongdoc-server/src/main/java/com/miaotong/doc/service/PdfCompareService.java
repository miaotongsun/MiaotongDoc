package com.miaotong.doc.service;

import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.service.storage.StorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Phase 14.U6: 文档对比服务 —— 逐页提取文本,按页对齐,做 line-level LCS diff
 * 返回:每页的状态(same/modified/added/removed)+ 差异 hunks(eq/add/del)
 */
@Service
public class PdfCompareService {

    private static final Logger log = LoggerFactory.getLogger(PdfCompareService.class);

    @Autowired private StorageService storageService;
    @Autowired private DocumentService documentService;

    public Map<String, Object> compare(Long docIdA, Long docIdB) {
        if (docIdA == null || docIdB == null) throw new BusinessException("文档 ID 不能为空");
        if (docIdA.equals(docIdB)) throw new BusinessException("请选择两个不同的文档");

        Document docA = documentService.getDocument(docIdA);
        Document docB = documentService.getDocument(docIdB);
        validatePdf(docA);
        validatePdf(docB);

        try {
            // 1. 提取两文档每页文本(行级)
            Map<Integer, List<String>> pagesA = extractLines(docIdA);
            Map<Integer, List<String>> pagesB = extractLines(docIdB);
            Set<Integer> allPages = new TreeSet<>();
            allPages.addAll(pagesA.keySet());
            allPages.addAll(pagesB.keySet());

            // 2. 按页对齐 + diff
            List<Map<String, Object>> pageResults = new ArrayList<>();
            int sameCount = 0, modifiedCount = 0, addedCount = 0, removedCount = 0;

            for (Integer p : allPages) {
                List<String> linesA = pagesA.getOrDefault(p, Collections.emptyList());
                List<String> linesB = pagesB.getOrDefault(p, Collections.emptyList());
                Map<String, Object> page = new LinkedHashMap<>();
                page.put("page", p);

                boolean aEmpty = linesA.isEmpty();
                boolean bEmpty = linesB.isEmpty();
                if (aEmpty && bEmpty) {
                    page.put("status", "same");
                    sameCount++;
                } else if (aEmpty) {
                    page.put("status", "added");
                    List<Map<String, Object>> hunks = new ArrayList<>();
                    for (String l : linesB) hunks.add(hunk("add", l));
                    page.put("diffHunks", hunks);
                    addedCount++;
                } else if (bEmpty) {
                    page.put("status", "removed");
                    List<Map<String, Object>> hunks = new ArrayList<>();
                    for (String l : linesA) hunks.add(hunk("del", l));
                    page.put("diffHunks", hunks);
                    removedCount++;
                } else if (linesA.equals(linesB)) {
                    page.put("status", "same");
                    sameCount++;
                } else {
                    page.put("status", "modified");
                    page.put("diffHunks", lcsDiff(linesA, linesB));
                    modifiedCount++;
                }
                pageResults.add(page);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("docA", Map.of("id", docA.getId(), "title", docA.getTitle()));
            result.put("docB", Map.of("id", docB.getId(), "title", docB.getTitle()));
            result.put("summary", Map.of(
                "totalPages", allPages.size(),
                "same", sameCount,
                "modified", modifiedCount,
                "added", addedCount,
                "removed", removedCount));
            result.put("pages", pageResults);
            log.info("文档对比完成: A={}({}), B={}({}), 同={}, 改={}, 新增={}, 删除={}",
                docIdA, pagesA.size(), docIdB, pagesB.size(),
                sameCount, modifiedCount, addedCount, removedCount);
            return result;
        } catch (Exception e) {
            log.error("文档对比失败: A={}, B={}", docIdA, docIdB, e);
            throw new BusinessException("文档对比失败: " + e.getMessage());
        }
    }

    /** 逐页提取文本行(去除空白行) */
    private Map<Integer, List<String>> extractLines(Long docId) throws IOException {
        byte[] bytes = storageService.load(documentService.getDocument(docId).getFilePath());
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            int total = pdf.getNumberOfPages();
            for (int i = 1; i <= total; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                List<String> lines = new ArrayList<>();
                if (text != null) {
                    for (String line : text.split("\\r?\\n")) {
                        String t = line.trim();
                        if (!t.isEmpty()) lines.add(t);
                    }
                }
                result.put(i, lines);
            }
        }
        return result;
    }

    /** 简单 LCS-based line diff:返回 type+text hunks 数组 */
    private List<Map<String, Object>> lcsDiff(List<String> a, List<String> b) {
        int n = a.size(), m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) dp[i][j] = dp[i + 1][j + 1] + 1;
                else dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        List<Map<String, Object>> hunks = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                hunks.add(hunk("eq", a.get(i)));
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                hunks.add(hunk("del", a.get(i)));
                i++;
            } else {
                hunks.add(hunk("add", b.get(j)));
                j++;
            }
        }
        while (i < n) { hunks.add(hunk("del", a.get(i++))); }
        while (j < m) { hunks.add(hunk("add", b.get(j++))); }
        return hunks;
    }

    private Map<String, Object> hunk(String type, String text) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("type", type);
        h.put("text", text);
        return h;
    }

    private void validatePdf(Document d) {
        if (d == null) throw new BusinessException("文档不存在");
        String path = d.getFilePath();
        if (path == null || !path.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("仅支持 PDF 文档对比");
        }
    }
}