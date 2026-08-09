package com.miaotong.doc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaotong.doc.entity.*;
import com.miaotong.doc.exception.BusinessException;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.*;
import com.miaotong.doc.service.ai.AiService;
import com.miaotong.doc.service.ai.DocumentContentService;
import com.miaotong.doc.service.ai.PromptTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractApprovalRepository approvalRepository;
    private final ContractApprovalNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final ContractParser contractParser;
    private final AuditService auditService;
    /** 2026-08-09 PDF 解析依赖：复用 DocumentContentService 提取 PDF 文本 */
    private final DocumentContentService documentContentService;
    /** 2026-08-09 PDF 解析依赖：调用 LLM 抽取结构化字段 */
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    /** 2026-08-09: 解析文档 — 支持 Word (正则) + PDF (LLM 抽取) */
    public Contract parseDocument(Long documentId) {
        Document doc = documentService.getDocument(documentId);
        String docType = doc.getDocType();
        if ("word".equals(docType)) {
            byte[] content = documentService.getFileContent(documentId);
            try {
                return contractParser.parse(content);
            } catch (IOException e) {
                throw new BusinessException("文档解析失败: " + e.getMessage());
            }
        } else if ("pdf".equals(docType)) {
            // PDF 走 LLM 抽取
            return parsePdfWithLlm(documentId);
        } else {
            throw new BusinessException("暂不支持解析 " + docType + " 类型文档（仅支持 Word 和 PDF）");
        }
    }

    /**
     * 2026-08-09: PDF 文档用 LLM 抽取结构化字段
     * 流程：extractText → PromptTemplates.CONTRACT_PARSE → chat → 解析 JSON
     */
    private Contract parsePdfWithLlm(Long documentId) {
        String text = documentContentService.extractText(documentId);
        if (text.isBlank()) {
            throw new BusinessException("PDF 文本提取为空，请确认文档已完成 OCR 识别");
        }
        // 限制长度（避免 token 超限）
        if (text.length() > 12000) {
            text = text.substring(0, 12000) + "\n...(已截断)";
        }
        String prompt = PromptTemplates.CONTRACT_PARSE.replace("{content}", text);
        String result = aiService.chat(prompt);
        log.info("PDF 合同抽取 LLM 响应(前 300 字): {}", result.substring(0, Math.min(300, result.length())));

        // 解析 LLM 返回的 JSON（兼容 markdown 代码块）
        String json = stripMarkdownCodeBlock(result);
        Contract contract = new Contract();
        try {
            JsonNode node = objectMapper.readTree(json);
            contract.setContractNo(textOrNull(node, "contractNo"));
            contract.setContractType(textOrNull(node, "contractType"));
            contract.setPartyA(textOrNull(node, "partyA"));
            contract.setPartyB(textOrNull(node, "partyB"));
            // 金额是数字
            JsonNode amountNode = node.path("amount");
            if (amountNode.isNumber()) {
                contract.setAmount(BigDecimal.valueOf(amountNode.asDouble()));
            } else if (amountNode.isTextual()) {
                try { contract.setAmount(new BigDecimal(amountNode.asText().replace(",", ""))); } catch (Exception ignored) {}
            }
            contract.setSigningDate(parseDateField(node.path("signingDate")));
            contract.setEffectiveDate(parseDateField(node.path("effectiveDate")));
            contract.setExpiryDate(parseDateField(node.path("expiryDate")));
        } catch (Exception e) {
            log.warn("PDF 合同抽取 JSON 解析失败: {}", e.getMessage());
            throw new BusinessException("PDF 抽取结果解析失败: " + e.getMessage());
        }
        return contract;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static LocalDate parseDateField(JsonNode n) {
        if (n.isMissingNode() || n.isNull() || !n.isTextual()) return null;
        String s = n.asText().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
            // 尝试 yyyy年M月d日 格式
            try {
                return LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"));
            } catch (Exception ignored2) { return null; }
        }
    }

    private static String stripMarkdownCodeBlock(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```json")) t = t.substring(7);
        else if (t.startsWith("```")) t = t.substring(3);
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.trim();
    }

    @Transactional
    public Contract createContract(Long documentId, Contract contractData, Long userId) {
        documentService.getDocument(documentId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("用户不存在"));

        Contract contract = new Contract();
        contract.setDocumentId(documentId);
        contract.setContractNo(contractData.getContractNo());
        contract.setContractType(contractData.getContractType());
        contract.setPartyA(contractData.getPartyA());
        contract.setPartyB(contractData.getPartyB());
        contract.setAmount(contractData.getAmount());
        contract.setCurrency(contractData.getCurrency() != null ? contractData.getCurrency() : "CNY");
        contract.setSigningDate(contractData.getSigningDate());
        contract.setEffectiveDate(contractData.getEffectiveDate());
        contract.setExpiryDate(contractData.getExpiryDate());
        contract.setRemarks(contractData.getRemarks());
        contract.setOwnerUserId(userId);
        contract.setDepartmentId(user.getDepartmentId());
        contract.setStatus("draft");

        contract = contractRepository.save(contract);
        auditService.log(userId, "CREATE", "CONTRACT", contract.getId(), null);
        return contract;
    }

    @Transactional
    public Contract updateContract(Long contractId, Contract data, Long userId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));

        if (!"draft".equals(contract.getStatus()) && !"rejected".equals(contract.getStatus())) {
            throw new BusinessException("只能编辑草稿或已拒绝状态的合同");
        }

        if (data.getContractNo() != null) contract.setContractNo(data.getContractNo());
        if (data.getContractType() != null) contract.setContractType(data.getContractType());
        if (data.getPartyA() != null) contract.setPartyA(data.getPartyA());
        if (data.getPartyB() != null) contract.setPartyB(data.getPartyB());
        if (data.getAmount() != null) contract.setAmount(data.getAmount());
        if (data.getSigningDate() != null) contract.setSigningDate(data.getSigningDate());
        if (data.getEffectiveDate() != null) contract.setEffectiveDate(data.getEffectiveDate());
        if (data.getExpiryDate() != null) contract.setExpiryDate(data.getExpiryDate());
        if (data.getRemarks() != null) contract.setRemarks(data.getRemarks());

        contract = contractRepository.save(contract);
        auditService.log(userId, "UPDATE", "CONTRACT", contractId, null);
        return contract;
    }

    /**
     * 提交审批 — 创建顺序审批链
     */
    @Transactional
    public void submitForApproval(Long contractId, List<Long> approverIds, LocalDate deadline, Long userId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));

        if (!"draft".equals(contract.getStatus()) && !"rejected".equals(contract.getStatus())) {
            throw new BusinessException("只能提交草稿或已拒绝状态的合同");
        }

        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException("至少选择一个审批人");
        }

        Document doc = documentRepository.findById(contract.getDocumentId())
                .orElseThrow(() -> new NotFoundException("关联文档不存在"));

        // 删除旧节点（重新提交场景）
        nodeRepository.deleteByContractId(contractId);

        // 创建审批节点链
        for (int i = 0; i < approverIds.size(); i++) {
            Long approverId = approverIds.get(i);
            User approver = userRepository.findById(approverId).orElse(null);
            ContractApprovalNode node = new ContractApprovalNode();
            node.setContractId(contractId);
            node.setStepOrder(i + 1);
            node.setApproverId(approverId);
            node.setApproverName(approver != null ? approver.getRealName() : "未知");
            node.setStatus(i == 0 ? "pending" : "waiting");
            nodeRepository.save(node);
        }

        // 快照文档哈希
        contract.setApprovedHash(doc.getFileHash());
        contract.setApprovedVersion(doc.getCurrentVersion());
        contract.setCurrentStep(1);
        contract.setStatus("pending_approval");
        // 锁定文档
        doc.setSigningLocked(true);
        documentRepository.save(doc);
        contractRepository.save(contract);

        // 记录审批日志
        ContractApproval approval = new ContractApproval();
        approval.setContractId(contractId);
        approval.setAction("submit");
        approval.setOperatorId(userId);
        userRepository.findById(userId).ifPresent(u -> approval.setOperatorName(u.getRealName()));
        approval.setRemark("提交审批，审批人: " + approverIds.size() + " 人");
        approvalRepository.save(approval);

        auditService.log(userId, "SUBMIT_APPROVAL", "CONTRACT", contractId, null);
    }

    /**
     * 审批通过 — 顺序推进到下一个节点
     */
    @Transactional
    public void approve(Long contractId, Long operatorId, String remark) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));

        if (!"pending_approval".equals(contract.getStatus())) {
            throw new BusinessException("合同不在审批中状态");
        }

        ContractApprovalNode currentNode = nodeRepository.findCurrentPendingNode(contractId)
                .orElseThrow(() -> new BusinessException("没有待审批的节点"));

        if (!currentNode.getApproverId().equals(operatorId)) {
            throw new BusinessException("您不是当前审批人");
        }

        // 当前节点通过
        currentNode.setStatus("approved");
        currentNode.setRemark(remark);
        currentNode.setActedAt(LocalDateTime.now());
        nodeRepository.save(currentNode);

        // 记录审批日志
        ContractApproval approval = new ContractApproval();
        approval.setContractId(contractId);
        approval.setAction("approve");
        approval.setOperatorId(operatorId);
        userRepository.findById(operatorId).ifPresent(u -> approval.setOperatorName(u.getRealName()));
        approval.setRemark(remark);
        approvalRepository.save(approval);

        // 查找下一个 waiting 节点
        List<ContractApprovalNode> allNodes = nodeRepository.findByContractIdOrderByStepOrderAsc(contractId);
        ContractApprovalNode nextNode = null;
        for (ContractApprovalNode node : allNodes) {
            if ("waiting".equals(node.getStatus())) {
                nextNode = node;
                break;
            }
        }

        if (nextNode != null) {
            // 推进到下一个节点
            nextNode.setStatus("pending");
            nodeRepository.save(nextNode);
            contract.setCurrentStep(nextNode.getStepOrder());
            contractRepository.save(contract);
        } else {
            // 所有节点通过 → 合同审批通过
            contract.setStatus("approved");
            contract.setCurrentStep(contract.getCurrentStep() + 1);
            // 解锁文档
            Document doc = documentRepository.findById(contract.getDocumentId()).orElse(null);
            if (doc != null) {
                doc.setSigningLocked(false);
                documentRepository.save(doc);
            }
            contractRepository.save(contract);

            auditService.log(operatorId, "APPROVE", "CONTRACT", contractId, null);
        }
    }

    /**
     * 审批拒绝 — 终止流程
     */
    @Transactional
    public void reject(Long contractId, Long operatorId, String remark) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));

        if (!"pending_approval".equals(contract.getStatus())) {
            throw new BusinessException("合同不在审批中状态");
        }

        ContractApprovalNode currentNode = nodeRepository.findCurrentPendingNode(contractId)
                .orElseThrow(() -> new BusinessException("没有待审批的节点"));

        if (!currentNode.getApproverId().equals(operatorId)) {
            throw new BusinessException("您不是当前审批人");
        }

        if (remark == null || remark.trim().isEmpty()) {
            throw new BusinessException("拒绝时必须填写备注");
        }

        // 当前节点拒绝
        currentNode.setStatus("rejected");
        currentNode.setRemark(remark);
        currentNode.setActedAt(LocalDateTime.now());
        nodeRepository.save(currentNode);

        // 合同状态回退为 rejected
        contract.setStatus("rejected");
        // 解锁文档
        Document doc = documentRepository.findById(contract.getDocumentId()).orElse(null);
        if (doc != null) {
            doc.setSigningLocked(false);
            documentRepository.save(doc);
        }
        contractRepository.save(contract);

        // 记录审批日志
        ContractApproval approval = new ContractApproval();
        approval.setContractId(contractId);
        approval.setAction("reject");
        approval.setOperatorId(operatorId);
        userRepository.findById(operatorId).ifPresent(u -> approval.setOperatorName(u.getRealName()));
        approval.setRemark(remark);
        approvalRepository.save(approval);

        auditService.log(operatorId, "REJECT", "CONTRACT", contractId, null);
    }

    /**
     * 撤回审批（仅发起人，审批中可撤回）
     */
    @Transactional
    public void cancel(Long contractId, Long operatorId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));

        if (!contract.getOwnerUserId().equals(operatorId)) {
            throw new BusinessException("只有发起人可以撤回");
        }

        if (!"pending_approval".equals(contract.getStatus())) {
            throw new BusinessException("合同不在审批中状态");
        }

        // 删除审批节点
        nodeRepository.deleteByContractId(contractId);

        // 合同回到草稿
        contract.setStatus("draft");
        contract.setCurrentStep(0);
        contract.setApprovedHash(null);
        contract.setApprovedVersion(null);
        // 解锁文档
        Document doc = documentRepository.findById(contract.getDocumentId()).orElse(null);
        if (doc != null) {
            doc.setSigningLocked(false);
            documentRepository.save(doc);
        }
        contractRepository.save(contract);

        // 记录审批日志
        ContractApproval approval = new ContractApproval();
        approval.setContractId(contractId);
        approval.setAction("cancel");
        approval.setOperatorId(operatorId);
        userRepository.findById(operatorId).ifPresent(u -> approval.setOperatorName(u.getRealName()));
        approvalRepository.save(approval);

        auditService.log(operatorId, "CANCEL_APPROVAL", "CONTRACT", contractId, null);
    }

    /**
     * 完整性校验
     */
    public Map<String, Object> checkIntegrity(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));

        Document doc = documentRepository.findById(contract.getDocumentId())
                .orElseThrow(() -> new NotFoundException("关联文档不存在"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractId", contractId);

        if (contract.getApprovedHash() == null) {
            result.put("intact", null);
            result.put("message", "合同尚未提交审批");
            return result;
        }

        boolean intact = contract.getApprovedHash().equals(doc.getFileHash());
        result.put("intact", intact);
        result.put("approvedVersion", contract.getApprovedVersion());
        result.put("currentVersion", doc.getCurrentVersion());

        if (intact) {
            result.put("message", "文档完整性校验通过");
        } else {
            result.put("message", "文档内容在审批后被修改，当前内容与审批通过时不同");
            result.put("warning", true);
        }

        return result;
    }

    public Contract getContract(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("合同不存在"));
    }

    public Page<Contract> getContracts(String status, String contractType, Long departmentId, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isEmpty()) {
            return contractRepository.searchByKeyword(keyword, pageable);
        }
        if (status != null && departmentId != null) {
            return contractRepository.findByStatusAndDepartmentId(status, departmentId, pageable);
        }
        if (status != null) {
            return contractRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        if (contractType != null) {
            return contractRepository.findByContractTypeOrderByCreatedAtDesc(contractType, pageable);
        }
        if (departmentId != null) {
            return contractRepository.findByDepartmentIdOrderByCreatedAtDesc(departmentId, pageable);
        }
        return contractRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public List<ContractApproval> getApprovalHistory(Long contractId) {
        return approvalRepository.findByContractIdOrderByCreatedAtDesc(contractId);
    }

    public List<ContractApprovalNode> getApprovalNodes(Long contractId) {
        return nodeRepository.findByContractIdOrderByStepOrderAsc(contractId);
    }

    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("draft", contractRepository.countByStatus("draft"));
        stats.put("pending_approval", contractRepository.countByStatus("pending_approval"));
        stats.put("approved", contractRepository.countByStatus("approved"));
        stats.put("rejected", contractRepository.countByStatus("rejected"));
        stats.put("expired", contractRepository.countByStatus("expired"));
        stats.put("total", contractRepository.count());
        return stats;
    }

    @Transactional
    public void deleteContract(Long contractId, Long userId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在"));
        if (!"draft".equals(contract.getStatus())) {
            throw new BusinessException("只能删除草稿状态的合同");
        }
        contractRepository.deleteById(contractId);
        auditService.log(userId, "DELETE", "CONTRACT", contractId, null);
    }
}
