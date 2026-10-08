package jp.ac.ccmc.zaikou.web;

import java.util.Map;
import java.util.LinkedHashMap;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class ApiErrorHandler {
    public record ErrorBody(String message, Map<String, String> fields) {}
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException ex) { return error(ex.status(), ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), "入力内容を確認してください。"));
        return ResponseEntity.badRequest().body(new ErrorBody("入力内容に誤りがあります。文字数・形式・数値の範囲を確認してください。", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> badRequest(Exception ex) { return error(HttpStatus.BAD_REQUEST, "リクエストの形式または値が正しくありません。"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(Exception ex) { return error(HttpStatus.CONFLICT, "商品コードが重複しているか、保存条件を満たしていません。"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> denied(Exception ex) { return error(HttpStatus.FORBIDDEN, "この操作は許可されていません。"); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> missing(Exception ex) { return error(HttpStatus.NOT_FOUND, "指定した API はありません。"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> method(Exception ex) { return error(HttpStatus.METHOD_NOT_ALLOWED, "この HTTP メソッドは使用できません。"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorBody> media(Exception ex) { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type を確認してください。"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception ex) {
        // Do not log request bodies, credentials or SQL parameter values.
        LoggerFactory.getLogger(getClass()).error("Request failed: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "処理できませんでした。時間をおいて再試行してください。");
    }
    private ResponseEntity<ErrorBody> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorBody(message, Map.of()));
    }
}
