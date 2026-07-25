package com.ppdv.api.common.exception;

/**
 * 业务异常：用于服务层抛出可展示给前端的错误信息。
 */
public class ServiceException extends RuntimeException {

    private Integer code;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Integer code) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
