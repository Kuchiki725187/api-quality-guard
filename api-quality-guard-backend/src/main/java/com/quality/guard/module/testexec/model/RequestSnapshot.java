package com.quality.guard.module.testexec.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 请求快照（test_result.request_snapshot JSON）
 */
@Data
@Builder
public class RequestSnapshot {

    private String method;

    private String url;

    private Map<String, String> headers;

    /** 请求体 JSON 文本；无体时为 null */
    private String body;

    private Long timeoutMs;
}
