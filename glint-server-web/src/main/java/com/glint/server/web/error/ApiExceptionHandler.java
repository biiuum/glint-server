package com.glint.server.web.error;

import com.glint.server.common.dto.ApiError;
import com.glint.server.biz.auth.AccountRequiredException;
import com.glint.server.biz.auth.AdminAccessDeniedException;
import com.glint.server.biz.auth.InvalidAnonymousSessionException;
import com.glint.server.biz.auth.ResourceAccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RestControllerAdvice
/** 将受控业务错误转换为稳定 API 响应，并避免回显敏感请求内容。 */
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> validation(Exception exception) {
        log.warn("api_validation_failed exceptionType={}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", validationMessage(exception)));
    }

    @ExceptionHandler(InvalidAnonymousSessionException.class)
    public ResponseEntity<ApiError> unauthorized() {
        log.warn("api_unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("UNAUTHORIZED", "匿名会话无效或已过期"));
    }

    @ExceptionHandler({AdminAccessDeniedException.class, ResourceAccessDeniedException.class})
    public ResponseEntity<ApiError> forbidden() {
        log.warn("api_forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "无权执行该操作"));
    }

    @ExceptionHandler(AccountRequiredException.class)
    public ResponseEntity<ApiError> accountRequired() {
        log.warn("api_account_required");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("ACCOUNT_REQUIRED", "请先用手机号登录后再使用该功能"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound() {
        log.warn("api_not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError() {
        log.error("api_internal_error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "服务暂时不可用"));
    }

    private ApiError error(String code, String message) {
        return new ApiError(code, message, UUID.randomUUID().toString());
    }

    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException invalid
                && invalid.getBindingResult().getFieldError() != null) {
            FieldError fieldError = invalid.getBindingResult().getFieldError();
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        if (exception instanceof IllegalArgumentException illegal
                && illegal.getMessage() != null && !illegal.getMessage().isBlank()) {
            return illegal.getMessage();
        }
        return "请求参数无效";
    }
}
