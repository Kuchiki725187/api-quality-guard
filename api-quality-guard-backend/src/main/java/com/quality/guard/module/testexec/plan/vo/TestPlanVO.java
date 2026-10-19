package com.quality.guard.module.testexec.plan.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试计划视图对象。列表页只需概要字段；详情页填充 caseIds 与 cases。
 */
@Data
public class TestPlanVO {

    private Long id;

    private String name;

    private String description;

    private Map<String, Object> executionConfig;

    private int caseCount;

    /** 关联用例 ID（仅详情填充） */
    private List<Long> caseIds;

    /** 关联用例摘要（仅详情填充） */
    private List<PlanCaseBrief> cases;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class PlanCaseBrief {

        private Long id;

        private String name;

        private String generationType;
    }
}
