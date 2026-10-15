package com.quality.guard.module.testexec.casegen.strategy;

import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;

import java.util.List;

/**
 * 用例生成策略：新增一种生成规则 = 新增一个实现类并交给 Spring 管理
 * TestCaseGenerator 通过注入的 List&lt;GenerationStrategy&gt; 自动发现全部策略
 */
public interface GenerationStrategy {

    /** 策略类型标识 */
    StrategyType getType();

    /**
     * 对给定接口执行生成
     *
     * @param apiDefinition 接口定义（含 requestSchema 参数与约束）
     * @return 候选用例列表；无可生成内容时返回空列表
     */
    List<GeneratedCase> generate(ApiDefinition apiDefinition);
}
