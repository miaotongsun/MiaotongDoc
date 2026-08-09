package com.miaotong.doc.controller;

import com.miaotong.doc.entity.ContractPayment;
import com.miaotong.doc.service.ContractPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 合同付款计划 API
 * 2026-08-09 新增 - 合同模块内容识别重塑 Phase 3
 *
 * 接口:
 *   GET    /api/contracts/{id}/payments         - 列表
 *   POST   /api/contracts/{id}/payments         - 新增
 *   GET    /api/contracts/{id}/payments/{pid}   - 详情
 *   PUT    /api/contracts/{id}/payments/{pid}   - 修改
 *   DELETE /api/contracts/{id}/payments/{pid}   - 删除
 *   PUT    /api/contracts/{id}/payments/{pid}/paid - 标记已付
 *   POST   /api/contracts/{id}/payments/extract  - AI 自动抽取
 */
@Slf4j
@RestController
@RequestMapping("/api/contracts/{id}/payments")
@RequiredArgsConstructor
public class ContractPaymentController {

    private final ContractPaymentService paymentService;

    @GetMapping
    public List<ContractPayment> list(@PathVariable Long id) {
        return paymentService.listByContract(id);
    }

    @PostMapping
    public ResponseEntity<ContractPayment> create(@PathVariable Long id,
                                                    @RequestBody ContractPayment data) {
        return ResponseEntity.ok(paymentService.create(id, data));
    }

    @GetMapping("/{pid}")
    public ResponseEntity<ContractPayment> get(@PathVariable Long id, @PathVariable Long pid) {
        return ResponseEntity.ok(paymentService.getById(pid));
    }

    @PutMapping("/{pid}")
    public ResponseEntity<ContractPayment> update(@PathVariable Long id,
                                                    @PathVariable Long pid,
                                                    @RequestBody ContractPayment data) {
        return ResponseEntity.ok(paymentService.update(pid, data));
    }

    @DeleteMapping("/{pid}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long pid) {
        paymentService.delete(pid);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{pid}/paid")
    public ResponseEntity<ContractPayment> markPaid(@PathVariable Long id,
                                                     @PathVariable Long pid,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        LocalDate paidDate = null;
        if (body != null && body.get("paidDate") instanceof String) {
            try { paidDate = LocalDate.parse((String) body.get("paidDate")); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(paymentService.markPaid(pid, paidDate));
    }

    /**
     * 2026-08-09:AI 自动从合同文档中抽取付款计划(返回候选列表,前端展示后用户确认再入库)
     */
    @PostMapping("/extract")
    public ResponseEntity<List<Map<String, Object>>> extractByAi(@PathVariable Long id,
                                                                  HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        log.info("AI 抽取付款计划: contractId={}, userId={}", id, userId);
        return ResponseEntity.ok(paymentService.extractByAi(id));
    }
}