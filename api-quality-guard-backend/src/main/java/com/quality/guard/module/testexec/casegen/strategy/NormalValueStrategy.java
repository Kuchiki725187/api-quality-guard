package com.quality.guard.module.testexec.casegen.strategy;

import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.casegen.support.BaseParamsFactory;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 正常值策略：生成一条"全部参数取合法值"的基准用例
 * 取值逻辑复用 BaseParamsFactory，本类只负责组装用例与断言
 */
@Component
@RequiredArgsConstructor
public class NormalValueStrategy implements GenerationStrategy {

    private final BaseParamsFactory baseParamsFactory;

    @Override
    public StrategyType getType() {
        return StrategyType.NORMAL;
    }

    @Override
    public List<GeneratedCase> generate(ApiDefinition apiDefinition) {
        //接口请求为空返回空集合
        if (apiDefinition.getRequestSchema() == null) {
            return List.of();
        }
        //生成合法的参数
        RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);

        List<AssertionRule> assertions = new ArrayList<>();
        //预期结果为状态码=200
        assertions.add(AssertionRule.statusCode(successStatusCode(apiDefinition)));
        //再对测试用例补充业务码断言，防止假通过
        addCodeAssertionIfPresent(apiDefinition, assertions);

        GeneratedCase generatedCase = GeneratedCase.builder()
                .name(apiDefinition.getName() + "-正常值")
                .requestParams(params)
                .assertionRules(assertions)
                .generationNote("NormalValueStrategy: 全部参数取合法值（约束内中间值/贴边值；pattern 约束暂未处理）")
                .strategyType(StrategyType.NORMAL)
                .build();
        return List.of(generatedCase);
    }

    private int successStatusCode(ApiDefinition apiDefinition) {
        ResponseSchemaDef response = apiDefinition.getResponseSchema();
        return response != null && response.getSuccessStatusCode() != null
                ? response.getSuccessStatusCode()
                : 200;
    }

    /** 响应结构顶层含 code 字段时，补充业务码断言（$.code = 0） */
    private void addCodeAssertionIfPresent(ApiDefinition apiDefinition, List<AssertionRule> assertions) {
        ResponseSchemaDef response = apiDefinition.getResponseSchema();
        if (response == null || response.getSchema() == null) {
            return;
        }
        if (response.getSchema().get("properties") instanceof Map<?, ?> properties
                && properties.containsKey("code")) {
            assertions.add(AssertionRule.jsonField("$.code", "EQUALS", 0));
        }
    }
}
