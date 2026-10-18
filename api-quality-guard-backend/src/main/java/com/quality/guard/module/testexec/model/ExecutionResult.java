package com.quality.guard.module.testexec.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 一次 HTTP 执行的结果（执行引擎输出 → 断言引擎输入）
 * errorMessage 非空表示网络层失败（无有效响应），用例判定为 ERROR，断言不判定
 */
@Data
@Builder
public class ExecutionResult {

    /** HTTP 状态码；网络失败时为 null */
    private Integer statusCode;

    /** 响应头（响应失败时为 null） */
    private Map<String, String> headers;

    /** 响应体原文（响应失败时为 null） */
    private String bodyText;

    /** 响应体是否因超长被截断 */
    private boolean bodyTruncated;

    /** 请求总耗时（毫秒） */
    private long elapsedMs;

    /** 网络层失败分类信息；成功时为 null */
    private String errorMessage;
}
