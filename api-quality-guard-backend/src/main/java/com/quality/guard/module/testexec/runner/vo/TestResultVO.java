package com.quality.guard.module.testexec.runner.vo;

import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.RequestSnapshot;
import com.quality.guard.module.testexec.model.ResponseSnapshot;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单用例执行结果视图对象
 * 注意：responseSnapshot / assertionResults 可能为 null（ERROR 场景）
 */
@Data
public class TestResultVO {

    private Long id;

    private Long runId;

    private Long caseId;

    private String caseName;

    /** PASSED / FAILED / ERROR */
    private String status;

    private RequestSnapshot requestSnapshot;

    private ResponseSnapshot responseSnapshot;

    private List<AssertionOutcome> assertionResults;

    private String errorMessage;

    private Long durationMs;

    private LocalDateTime createdAt;
}
