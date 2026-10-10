package com.quality.guard.common.exception;

import com.quality.guard.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：表示可预期的业务规则失败（如资源不存在、状态非法）
 * 由全局异常处理器转换为统一响应
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ResultCode.BAD_REQUEST, message);
    }

    public BusinessException(ResultCode resultCode) {
        this(resultCode, resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
