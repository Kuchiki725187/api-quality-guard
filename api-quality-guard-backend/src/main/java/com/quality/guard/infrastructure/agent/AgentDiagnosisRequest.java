package com.quality.guard.infrastructure.agent;

import com.quality.guard.module.testexec.model.AssertionOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 发送给诊断 Agent 的请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDiagnosisRequest {

    private Long caseId;

    private Long apiId;

    private String caseName;

    private String apiMethod;

    private String apiPath;

    private String apiBaseUrl;

    private String apiDescription;

    private Object apiRequestSchema;

    private Object apiResponseSchema;

    private Object requestParams;

    private Object requestSnapshot;

    private Object responseSnapshot;

    private List<AssertionOutcome> assertionResults;

    private String errorMessage;

    private String extraContext;
}
