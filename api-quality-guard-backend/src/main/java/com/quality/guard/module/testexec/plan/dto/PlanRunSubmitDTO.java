package com.quality.guard.module.testexec.plan.dto;

import lombok.Data;

/**
 * 提交计划执行时的一次性覆盖参数（可选）。
 */
@Data
public class PlanRunSubmitDTO {

    /** 覆盖被测服务地址（环境切换）；为空则按接口定义中的 baseUrl */
    private String baseUrl;
}
