package com.miaotong.doc.controller;

import com.miaotong.doc.entity.Contract;
import com.miaotong.doc.exception.NotFoundException;
import com.miaotong.doc.repository.ContractRepository;
import com.miaotong.doc.service.ai.AiService;
import com.miaotong.doc.service.ai.DocumentContentService;
import com.miaotong.doc.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 合同 AI 审查 SSE 控制器
 *
 * 用例：从合同 Word/PDF 文档中自动审查法律/商业风险，输出结构化 JSON。
 * 接口：POST /api/contracts/{id}/ai/review
 *
 * 响应：SSE 流
 *   event: docStatus   { contractId, status }
 *   event: delta       { content: "..." }      // 流式 JSON 字符
 *   event: done        { review: {...} }       // 解析后的结构化结果
 *   event: error       { code, message }
 *
 * @since 2026-08-09 合同 AI 审查
 */
@Slf4j
@RestController
@RequestMapping("/api/contracts/{id}/ai/review")
@RequiredArgsConstructor
public class ContractAiController {

    private final ContractRepository contractRepository;
    private final DocumentContentService documentContentService;
    private final AiService aiService;
    private final JwtUtil jwtUtil;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter reviewStream(
            @PathVariable Long id,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 1. 鉴权
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            SseEmitter e = new SseEmitter(1000L);
            e.completeWithError(new RuntimeException("未登录"));
            return e;
        }
        String token = auth.substring(7);
        if (!jwtUtil.validateToken(token)) {
            SseEmitter e = new SseEmitter(1000L);
            e.completeWithError(new RuntimeException("Token 无效"));
            return e;
        }

        response.setBufferSize(0);
        response.setHeader("X-Accel-Buffering", "no");

        // 2. AI 未配置检测 → 统一 event:error
        if (!aiService.isConfigured()) {
            SseEmitter earlyErr = new SseEmitter(1000L);
            sendEvent(earlyErr, "error", Map.of(
                    "code", "AI_NOT_CONFIGURED",
                    "message", "LLM 服务未配置,请前往管理后台 → AI 配置 → 添加 LLM 类型 Provider"));
            earlyErr.complete();
            return earlyErr;
        }

        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                // 3. 查询合同
                Contract contract = contractRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("合同不存在"));

                sendEvent(emitter, "docStatus", Map.of(
                        "contractId", id,
                        "status", "loading",
                        "contractNo", contract.getContractNo() == null ? "" : contract.getContractNo()));

                // 4. 提取合同文本
                String fullText = extractContractText(contract);
                if (fullText == null || fullText.isBlank()) {
                    sendEvent(emitter, "error", Map.of(
                            "code", "TEXT_EMPTY",
                            "message", "合同文档内容为空,无法审查"));
                    emitter.complete();
                    return;
                }

                log.info("合同审查: contractId={}, textLen={}", id, fullText.length());

                // 5. 调用 AiService.reviewContract 获取结构化 Map
                //    由于 reviewContract 是同步阻塞调用,会一次性返回,这里模拟"流式"以保持与 PDF SSE 接口一致
                Map<String, Object> review = aiService.reviewContract(fullText);

                sendEvent(emitter, "delta", Map.of("content", "AI 审查完成,正在渲染结果..."));

                // 6. done 事件携带完整结果
                sendEvent(emitter, "done", Map.of(
                        "contractId", id,
                        "review", review,
                        "engine", "contract-review"));
                emitter.complete();

            } catch (Exception e) {
                log.error("合同 AI 审查失败", e);
                try {
                    sendEvent(emitter, "error", Map.of("message", "服务异常: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }, "contract-ai-review-" + id).start();

        return emitter;
    }

    /**
     * 从合同关联的文档中提取文本（支持 Word / PDF）
     */
    private String extractContractText(Contract contract) {
        try {
            Long docId = contract.getDocumentId();
            if (docId == null) return null;
            String text = documentContentService.extractText(docId);
            // 限制最大长度(避免 token 超限)
            if (text != null && text.length() > 12000) {
                text = text.substring(0, 12000) + "\n...(已截断)";
            }
            return text;
        } catch (Exception e) {
            log.warn("提取合同文档文本失败: contractId={}", contract.getId(), e);
            return null;
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception e) {
            log.debug("SSE 发送失败 ({}): {}", name, e.getMessage());
        }
    }
}