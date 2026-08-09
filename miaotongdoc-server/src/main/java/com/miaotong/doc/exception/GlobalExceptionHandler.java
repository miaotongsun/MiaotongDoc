package com.miaotong.doc.exception;

import com.miaotong.doc.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 2026-08-09: 防信息泄露 sanitize
     *
     * 业务代码里常见 `new BusinessException("xxx失败: " + e.getMessage())`,
     * e.getMessage() 可能包含 SQL/路径/IP/Java 内部细节,直接透传给前端会泄露。
     *
     * 规则:如果 message 命中"内部细节特征" → 替换成通用文案;
     * 否则保留(说明是业务代码主动构造的中文文案,可信)。
     */
    private static String sanitizeMessage(String msg) {
        if (msg == null || msg.isBlank()) return "操作失败,请重试";
        // 内部细节特征(命中任一就替换)
        boolean suspicious =
                msg.contains("java.") || msg.contains("javax.")
                || msg.contains("org.springframework") || msg.contains("org.apache")
                || msg.contains("com.miaotong")   // 自己的内部包路径也算可疑
                || msg.contains("Exception:") || msg.contains("Throwable")
                || msg.contains(" at ")          // Java 堆栈 "at xxx.xxx.xxx(...)"
                || msg.contains("SQL") || msg.contains("psql") || msg.contains("relation \"")
                || msg.contains("column \"") || msg.contains("constraint \"")
                || msg.contains("/data/") || msg.contains("/opt/") || msg.contains("/usr/")
                || msg.contains("classpath:") || msg.contains(".jar")
                || msg.contains("Connection refused") || msg.contains("UnknownHost")
                || msg.contains("172.") || msg.contains("10.") || msg.contains("192.168.")
                || msg.contains("redis") || msg.contains("rabbitmq") || msg.contains("postgres")
                || msg.contains("minio") || msg.contains("docling:")
                || msg.contains("paddleocr") || msg.contains("tesseract")
                || msg.contains("pool") || msg.contains("HikariCP")
                || msg.contains("NullPointer") || msg.contains("ClassCast")
                || msg.contains("IllegalArgument") || msg.contains("IllegalState")
                || msg.contains("StackOverflow") || msg.contains("OutOfMemory")
                || msg.startsWith("Error: ") || msg.startsWith("Caused by:");
        if (suspicious) {
            log.warn("业务异常 message 含内部细节,已 sanitize。原始: {}", msg);
            return "操作失败,请重试或联系管理员";
        }
        return msg;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        // 2026-08-09: sanitize message,防止业务代码误把内部异常 getMessage() 拼进去泄露 SQL/路径/IP
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(e.getCode(), sanitizeMessage(e.getMessage()), null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse(404, sanitizeMessage(e.getMessage()), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse(403, "无权访问", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> fields.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "参数校验失败", fields));
    }

    /**
     * JSON 请求体格式错误 / 类型不匹配(如传对象数组给 Map<String,String>):
     * 返 400 而非 500
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
        org.springframework.http.converter.HttpMessageNotReadableException.class
    )
    public ResponseEntity<ErrorResponse> handleNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "请求体格式错误或参数类型不匹配", null));
    }

    /**
     * Phase 27 测试报告:@PathVariable Long 收到 "abc" 时抛 NumberFormatException,
     * 统一 400 返回(不暴露 500 内部错误)
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ErrorResponse> handleNumberFormat(NumberFormatException e) {
        log.warn("路径参数数字格式错误: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "路径参数格式错误,必须是数字", null));
    }

    /**
     * Phase 27:@PathVariable 类型不匹配(如期望 Long 收到 "abc")
     */
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.beans.TypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleTypeMismatch(Exception e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "参数类型不匹配", null));
    }

    /**
     * 2026-08-09: 下游服务超时/网络不可达(调 Docling / PaddleOCR / LLM 等)
     * RestTemplate 抛 ResourceAccessException,内部 cause 可能是 SocketTimeoutException。
     * 统一返回 504 + 中文提示,前端不再看到英文 timeout 异常。
     */
    @ExceptionHandler({
            org.springframework.web.client.ResourceAccessException.class,
            java.net.SocketTimeoutException.class,
            TimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handleUpstreamTimeout(Exception e) {
        log.warn("下游服务超时或网络异常: {}", e.getMessage());
        return ResponseEntity.status(504)
                .body(new ErrorResponse(504, "服务繁忙或任务超时,请稍后重试", null));
    }

    /**
     * 下游服务返回 5xx(LLM/OCR Provider 自身错误)
     */
    @ExceptionHandler(org.springframework.web.client.HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleUpstream5xx(
            org.springframework.web.client.HttpServerErrorException e) {
        log.warn("下游服务 5xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        return ResponseEntity.status(502)
                .body(new ErrorResponse(502, "服务繁忙,请稍后重试", null));
    }

    /**
     * 下游服务返回 4xx(配置错误/鉴权失败)
     */
    @ExceptionHandler(org.springframework.web.client.HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleUpstream4xx(
            org.springframework.web.client.HttpClientErrorException e) {
        log.warn("下游服务 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        return ResponseEntity.status(502)
                .body(new ErrorResponse(502, "服务调用参数错误,请联系管理员", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(500, "服务器内部错误", null));
    }

    /**
     * 数据库外键/唯一约束违反(如评论 documentId 不存在 → 抛 DataIntegrityViolationException):
     * 捕获并返回 400 而非 500,避免前端误判为服务器故障
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性异常: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "关联数据不存在或违反唯一约束", null));
    }

}
