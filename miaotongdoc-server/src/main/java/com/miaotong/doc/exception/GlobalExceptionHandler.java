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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(e.getCode(), e.getMessage(), null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse(404, e.getMessage(), null));
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
