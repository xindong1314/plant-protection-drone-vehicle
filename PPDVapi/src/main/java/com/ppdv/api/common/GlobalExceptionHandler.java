package com.ppdv.api.common;

import com.ppdv.api.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理，统一返回若依风格 AjaxResult。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleServiceException(ServiceException ex) {
        Integer code = ex.getCode();
        return code != null ? AjaxResult.error(code, ex.getMessage()) : AjaxResult.error(ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public AjaxResult handleAccessDenied(AccessDeniedException ex) {
        return AjaxResult.error(403, "没有权限，请联系管理员授权");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult handleUnreadable(HttpMessageNotReadableException ex) {
        return AjaxResult.error(400, "请求体解析失败，请检查 JSON 格式与 UTF-8 编码");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("参数校验失败");
        return AjaxResult.error(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult handleIllegalArgument(IllegalArgumentException ex) {
        return AjaxResult.error(400, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public AjaxResult handleIllegalState(IllegalStateException ex) {
        return AjaxResult.error(409, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public AjaxResult handleException(Exception ex) {
        log.error("系统异常", ex);
        return AjaxResult.error("系统内部错误：" + ex.getMessage());
    }
}
