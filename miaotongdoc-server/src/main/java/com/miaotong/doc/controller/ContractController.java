package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Contract;
import com.miaotong.doc.entity.ContractApproval;
import com.miaotong.doc.entity.ContractApprovalNode;
import com.miaotong.doc.entity.Document;
import com.miaotong.doc.service.ContractService;
import com.miaotong.doc.service.DocumentService;
import com.miaotong.doc.repository.UserRepository;
import com.miaotong.doc.repository.DepartmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @PostMapping("/parse/{documentId}")
    public ResponseEntity<Map<String, Object>> parseDocument(@PathVariable Long documentId) {
        Contract parsed = contractService.parseDocument(documentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractNo", parsed.getContractNo());
        result.put("contractType", parsed.getContractType());
        result.put("partyA", parsed.getPartyA());
        result.put("partyB", parsed.getPartyB());
        result.put("amount", parsed.getAmount());
        result.put("signingDate", parsed.getSigningDate());
        result.put("effectiveDate", parsed.getEffectiveDate());
        result.put("expiryDate", parsed.getExpiryDate());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createContract(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        Long documentId = Long.valueOf(request.get("documentId").toString());

        Contract data = new Contract();
        data.setContractNo((String) request.get("contractNo"));
        data.setContractType((String) request.get("contractType"));
        data.setPartyA((String) request.get("partyA"));
        data.setPartyB((String) request.get("partyB"));
        if (request.get("amount") != null) {
            data.setAmount(new java.math.BigDecimal(request.get("amount").toString()));
        }
        data.setSigningDate(parseDate(request.get("signingDate")));
        data.setEffectiveDate(parseDate(request.get("effectiveDate")));
        data.setExpiryDate(parseDate(request.get("expiryDate")));
        data.setRemarks((String) request.get("remarks"));

        Contract contract = contractService.createContract(documentId, data, userId);
        return ResponseEntity.ok(toMap(contract));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listContracts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Contract> contracts = contractService.getContracts(status, contractType, departmentId, keyword, pageRequest);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", contracts.getContent().stream().map(this::toMap).toList());
        result.put("totalElements", contracts.getTotalElements());
        result.put("totalPages", contracts.getTotalPages());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getContract(@PathVariable Long id) {
        Contract contract = contractService.getContract(id);
        Map<String, Object> result = toMap(contract);

        // 审批节点
        List<ContractApprovalNode> nodes = contractService.getApprovalNodes(id);
        result.put("approvalNodes", nodes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("stepOrder", n.getStepOrder());
            m.put("approverId", n.getApproverId());
            m.put("approverName", n.getApproverName());
            m.put("status", n.getStatus());
            m.put("remark", n.getRemark());
            m.put("actedAt", n.getActedAt());
            return m;
        }).toList());

        // 审批历史
        List<ContractApproval> approvals = contractService.getApprovalHistory(id);
        result.put("approvals", approvals.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("action", a.getAction());
            m.put("operatorName", a.getOperatorName());
            m.put("remark", a.getRemark());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateContract(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");

        Contract data = new Contract();
        data.setContractNo((String) request.get("contractNo"));
        data.setContractType((String) request.get("contractType"));
        data.setPartyA((String) request.get("partyA"));
        data.setPartyB((String) request.get("partyB"));
        if (request.get("amount") != null) {
            data.setAmount(new java.math.BigDecimal(request.get("amount").toString()));
        }
        data.setSigningDate(parseDate(request.get("signingDate")));
        data.setEffectiveDate(parseDate(request.get("effectiveDate")));
        data.setExpiryDate(parseDate(request.get("expiryDate")));
        data.setRemarks((String) request.get("remarks"));

        Contract contract = contractService.updateContract(id, data, userId);
        return ResponseEntity.ok(toMap(contract));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, String>> submitForApproval(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        @SuppressWarnings("unchecked")
        List<Long> approverIds = ((List<Number>) request.get("approverIds")).stream().map(Number::longValue).toList();
        java.time.LocalDate deadline = parseDate(request.get("deadline"));
        contractService.submitForApproval(id, approverIds, deadline, userId);
        return ResponseEntity.ok(Map.of("message", "已提交审批"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, String>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String remark = request != null ? (String) request.get("remark") : null;
        contractService.approve(id, userId, remark);
        return ResponseEntity.ok(Map.of("message", "审批通过"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, String>> reject(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String remark = (String) request.get("remark");
        contractService.reject(id, userId, remark);
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, String>> cancel(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        contractService.cancel(id, userId);
        return ResponseEntity.ok(Map.of("message", "已撤回"));
    }

    @GetMapping("/{id}/integrity")
    public ResponseEntity<Map<String, Object>> checkIntegrity(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.checkIntegrity(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteContract(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        contractService.deleteContract(id, userId);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(contractService.getStatistics());
    }

    private Map<String, Object> toMap(Contract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("documentId", c.getDocumentId());
        m.put("contractNo", c.getContractNo());
        m.put("contractType", c.getContractType());
        m.put("partyA", c.getPartyA());
        m.put("partyB", c.getPartyB());
        m.put("amount", c.getAmount());
        m.put("currency", c.getCurrency());
        m.put("signingDate", c.getSigningDate());
        m.put("effectiveDate", c.getEffectiveDate());
        m.put("expiryDate", c.getExpiryDate());
        m.put("status", c.getStatus());
        m.put("ownerUserId", c.getOwnerUserId());
        m.put("departmentId", c.getDepartmentId());
        m.put("currentStep", c.getCurrentStep());
        m.put("approvedVersion", c.getApprovedVersion());
        m.put("remarks", c.getRemarks());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());

        try {
            Document doc = documentService.getDocument(c.getDocumentId());
            m.put("documentTitle", doc.getTitle());
            m.put("signingLocked", doc.getSigningLocked());
        } catch (Exception e) {
            m.put("documentTitle", "(已删除)");
            m.put("signingLocked", false);
        }

        userRepository.findById(c.getOwnerUserId()).ifPresent(u -> m.put("ownerName", u.getRealName()));
        if (c.getDepartmentId() != null) {
            departmentRepository.findById(c.getDepartmentId()).ifPresent(d -> m.put("departmentName", d.getName()));
        }
        return m;
    }

    private java.time.LocalDate parseDate(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return java.time.LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
