package com.quality.guard.module.ai.report.model;

import lombok.Data;

/**
 * 风险接口查询行：接口元数据 + 最近一次失败/异常结果
 */
@Data
public class ApiRiskRow {

    private Long apiId;

    private String name;

    private String method;

    private String path;

    private Long resultId;

    private String status;
}
