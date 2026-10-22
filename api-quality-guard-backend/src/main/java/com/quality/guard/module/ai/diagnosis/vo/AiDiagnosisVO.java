package com.quality.guard.module.ai.diagnosis.vo;

import com.quality.guard.module.ai.diagnosis.model.DiagnosisPayload;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 诊断记录视图对象
 */
@Data
public class AiDiagnosisVO {

    private Long id;

    private Long testResultId;

    /** 关联用例名（查询时填充） */
    private String caseName;

    private String inputSummary;

    private DiagnosisPayload candidateCauses;

    private String rawModelOutput;

    private String modelName;

    private Long durationMs;

    private Integer inputTokens;

    private Integer outputTokens;

    private String humanFeedback;

    private String humanComment;

    private LocalDateTime createdAt;
}
