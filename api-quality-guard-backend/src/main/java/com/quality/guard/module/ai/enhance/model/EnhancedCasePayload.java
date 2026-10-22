package com.quality.guard.module.ai.enhance.model;

import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 返回的单条候选用例（落库前转换为 test_case）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedCasePayload {

    private String name;

    private RequestParams requestParams;

    private List<AssertionRule> assertionRules;

    /** 业务场景与预期依据说明 */
    private String generationNote;
}
