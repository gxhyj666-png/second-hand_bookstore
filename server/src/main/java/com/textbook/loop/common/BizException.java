package com.textbook.loop.common;

/**
 * 业务异常，由 GlobalExceptionHandler 统一转成 { code, message }。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException badRequest(String message) {
        return new BizException(40000, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(40100, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(40300, message);
    }

    public static BizException notFound(String message) {
        return new BizException(40400, message);
    }

    public static BizException conflict(String message) {
        return new BizException(40900, message);
    }
}
