package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.Contract;
import com.miaotong.doc.entity.ContractPayment;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.ContractPaymentRepository;
import com.miaotong.doc.repository.ContractRepository;
import com.miaotong.doc.service.ai.AiService;
import com.miaotong.doc.service.ai.DocumentContentService;
import com.miaotong.doc.service.ai.PromptTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 合同付款计划服务
 * 2026-08-09 新增 - 支持 AI 自动抽取 + CRUD + 到期提醒
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractPaymentService {

    private final ContractPaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final DocumentContentService documentContentService;
    private final DocumentService documentService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public List<ContractPayment> listByContract(Long contractId) {
        return paymentRepository.findByContractIdOrderBySequenceAsc(contractId);
    }

    public ContractPayment getById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("付款计划不存在"));
    }

    @Transactional
    public ContractPayment create(Long contractId, ContractPayment data) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));
        data.setContractId(contractId);
        if (data.getStatus() == null || data.getStatus().isBlank()) {
            data.setStatus("pending");
        }
        if (data.getCurrency() == null || data.getCurrency().isBlank()) {
            data.setCurrency("CNY");
        }
        if (data.getSequence() == null) {
            // 自动取下一个期次
            List<ContractPayment> existing = listByContract(contractId);
            int next = existing.stream().mapToInt(ContractPayment::getSequence).max().orElse(0) + 1;
            data.setSequence(next);
        }
        // 自动检查 overdue 状态
        if ("pending".equals(data.getStatus()) && data.getDueDate() != null && data.getDueDate().isBefore(LocalDate.now())) {
            data.setStatus("overdue");
        }
        log.info("创建付款计划: contractId={}, title={}, amount={}, dueDate={}",
                contractId, data.getTitle(), data.getAmount(), data.getDueDate());
        return paymentRepository.save(data);
    }

    @Transactional
    public ContractPayment update(Long id, ContractPayment data) {
        ContractPayment existing = getById(id);
        if (data.getTitle() != null) existing.setTitle(data.getTitle());
        if (data.getAmount() != null) existing.setAmount(data.getAmount());
        if (data.getCurrency() != null) existing.setCurrency(data.getCurrency());
        if (data.getDueDate() != null) existing.setDueDate(data.getDueDate());
        if (data.getPaidDate() != null) existing.setPaidDate(data.getPaidDate());
        if (data.getStatus() != null) existing.setStatus(data.getStatus());
        if (data.getRemarks() != null) existing.setRemarks(data.getRemarks());
        if (data.getSequence() != null) existing.setSequence(data.getSequence());
        // 重新计算 overdue
        if ("pending".equals(existing.getStatus()) && existing.getDueDate() != null && existing.getDueDate().isBefore(LocalDate.now())) {
            existing.setStatus("overdue");
        }
        return paymentRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        paymentRepository.deleteById(id);
    }

    @Transactional
    public ContractPayment markPaid(Long id, LocalDate paidDate) {
        ContractPayment existing = getById(id);
        existing.setStatus("paid");
        existing.setPaidDate(paidDate != null ? paidDate : LocalDate.now());
        existing.setReminderSent(true); // 已付的不会再提醒
        return paymentRepository.save(existing);
    }

    /**
     * 2026-08-09:AI 自动从合同文档中抽取付款计划
     * 返回候选列表,用户在前端确认后再调 create()
     */
    public List<Map<String, Object>> extractByAi(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));
        if (contract.getDocumentId() == null) {
            throw new BusinessException("合同无关联文档,无法 AI 抽取");
        }
        // 从合同关联的文档中提取纯文本(支持 Word/PDF/MD)
        Long docId = contract.getDocumentId();
        Document doc = documentService.getDocument(docId);
        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        String text = documentContentService.extractText(docId);
        if (text == null || text.isBlank()) {
            // 2026-08-09:只有文档真的无文本时才报错。
            // 注意:文档有内容但 AI 未识别到付款条款是正常场景(LLM 返回 []),
            // 不应走错误分支,前端按"未识别到"提示用户手动添加。
            String hint;
            if ("pdf".equals(fileType)) {
                hint = "PDF 文档无可提取文本,可能是扫描件,请先在 PDF 编辑器中完成 OCR 识别";
            } else if ("word".equals(fileType) || "docx".equals(fileType) || "doc".equals(fileType)) {
                hint = "Word 文档无可提取文本,可能文档内容为空或为纯图片,请确认文档包含可识别的文字内容";
            } else if ("md".equals(fileType) || "markdown".equals(fileType)) {
                hint = "Markdown 文档内容为空,请确认文档已保存";
            } else {
                hint = "文档无可提取文本,请确认文档内容非空";
            }
            throw new BusinessException("合同文档无可识别文本,无法 AI 抽取(" + hint + ")");
        }
        if (text.length() > 12000) {
            text = text.substring(0, 12000) + "\n...(已截断)";
        }
        String prompt = PromptTemplates.CONTRACT_PAYMENT_EXTRACT.replace("{content}", text);
        String raw = aiService.chat(prompt);
        log.info("AI 付款计划抽取 contractId={}: 响应前 300 字={}",
                contractId, raw.substring(0, Math.min(300, raw.length())));

        // 解析 JSON:返回空数组是正常情况(LLM 找不到付款条款),只解析失败才报错
        String json = stripMarkdownCodeBlock(raw);
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode arr = node.isArray() ? node : node.path("payments");
            if (!arr.isArray()) {
                log.warn("AI 付款计划抽取返回非数组(可能合同无付款条款): {}", json);
                return result;
            }
            for (JsonNode item : arr) {
                Map<String, Object> plan = new LinkedHashMap<>();
                plan.put("title", textOr(item, "title", null));
                plan.put("amount", numberOr(item, "amount", null));
                plan.put("dueDate", dateOr(item, "dueDate", null));
                plan.put("remarks", textOr(item, "remarks", null));
                plan.put("currency", textOr(item, "currency", "CNY"));
                // 至少要有 dueDate 或 amount
                if (plan.get("dueDate") != null || plan.get("amount") != null) {
                    result.add(plan);
                }
            }
        } catch (Exception e) {
            log.warn("AI 付款计划 JSON 解析失败: {}", e.getMessage());
            throw new BusinessException("AI 抽取结果解析失败: " + e.getMessage());
        }
        return result;
    }

    private static String stripMarkdownCodeBlock(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```json")) t = t.substring(7);
        else if (t.startsWith("```")) t = t.substring(3);
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.trim();
    }

    private static String textOr(JsonNode n, String f, String def) {
        JsonNode v = n.path(f);
        if (v.isMissingNode() || v.isNull()) return def;
        return v.asText();
    }

    private static Object numberOr(JsonNode n, String f, Object def) {
        JsonNode v = n.path(f);
        if (v.isMissingNode() || v.isNull()) return def;
        if (v.isNumber()) return BigDecimal.valueOf(v.asDouble());
        try { return new BigDecimal(v.asText().replace(",", "")); } catch (Exception e) { return def; }
    }

    private static LocalDate dateOr(JsonNode n, String f, LocalDate def) {
        JsonNode v = n.path(f);
        if (v.isMissingNode() || v.isNull()) return def;
        String s = v.asText().trim();
        if (s.isEmpty()) return def;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy年M月d日")); } catch (Exception e2) { return def; }
        }
    }
}