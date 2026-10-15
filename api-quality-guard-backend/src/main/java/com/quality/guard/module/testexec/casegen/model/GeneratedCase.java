package com.quality.guard.module.testexec.casegen.model;

import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 生成引擎产出的"候选用例"（尚未落库）
 * 落库时由 casegen 的 Service 转换为 TestCase 实体
 * （generationType 固定为 RULE，generationNote 取本对象的说明）
 */
@Data
@Builder
public class GeneratedCase {

    /** 用例名称建议格式："{接口名}-{场景}"，如 "查询用户-必填缺失(id)" */
    private String name;

    /** 具体请求参数值（按位置分组） */
    private RequestParams requestParams;

    /** 默认断言规则（至少状态码断言） */
    private List<AssertionRule> assertionRules;

    /** 生成来源说明，用于"可解释"验收，如 "BoundaryValueStrategy: id minimum=1，取 0（minimum-1）" */
    private String generationNote;

    /** 产出该用例的策略类型 */
    private StrategyType strategyType;
}
