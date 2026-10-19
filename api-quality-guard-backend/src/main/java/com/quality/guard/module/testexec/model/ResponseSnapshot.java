package com.quality.guard.module.testexec.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 响应快照（test_result.response_snapshot JSON）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseSnapshot {

    private Integer statusCode;

    private Map<String, String> headers;

    /** 响应体文本；超过上限时截断 */
    private String body;

    private boolean bodyTruncated;

    private Long elapsedMs;
}
