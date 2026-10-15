package com.quality.guard.module.testexec.casegen.strategy;

import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.casegen.support.BaseParamsFactory;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.ParamDef;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 必填缺失策略：为每个必填参数生成一条"缺失该参数"的用例（一次只缺一个）
 */
@Component
@RequiredArgsConstructor
public class RequiredFieldStrategy implements GenerationStrategy {

    private final BaseParamsFactory baseParamsFactory;

    @Override
    public StrategyType getType() {
        return StrategyType.REQUIRED_MISSING;
    }

    @Override
    public List<GeneratedCase> generate(ApiDefinition apiDefinition) {
        RequestSchemaDef requestSchema = apiDefinition.getRequestSchema();
        if (requestSchema == null || requestSchema.getParams() == null) {
            return List.of();
        }
        List<GeneratedCase> cases = new ArrayList<>();

        for (ParamDef param : requestSchema.getParams()) {
            if ("body".equals(param.getIn())) {
                cases.addAll(generateForBody(apiDefinition, param));
            } else if (param.isRequired()) {
                // 顶层必填参数（path / query / header）：基准参数集上移除该参数
                RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);
                removeTopLevel(params, param.getIn(), param.getName());
                cases.add(buildCase(apiDefinition, params,
                        apiDefinition.getName() + "-必填缺失(" + param.getName() + ")",
                        "RequiredFieldStrategy: 移除 " + param.getIn() + " 必填参数 " + param.getName()));
            }
        }
        return cases;
    }

    private List<GeneratedCase> generateForBody(ApiDefinition apiDefinition, ParamDef bodyParam) {
        List<GeneratedCase> cases = new ArrayList<>();

        // body 整体必填：生成一条 body 整体缺失的用例
        if (bodyParam.isRequired()) {
            RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);
            params.setBody(null);
            cases.add(buildCase(apiDefinition, params,
                    apiDefinition.getName() + "-必填缺失(body)",
                    "RequiredFieldStrategy: body 整体必填，移除 body"));
        }

        // body 内必填字段：按 schema.required 列表逐个移除
        Map<String, Object> schema = bodyParam.getSchema();
        if (schema != null && schema.get("required") instanceof List<?> requiredList) {
            for (Object fieldObj : requiredList) {
                String fieldName = String.valueOf(fieldObj);
                RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);
                if (params.getBody() instanceof Map<?, ?> bodyRaw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = (Map<String, Object>) bodyRaw;
                    body.remove(fieldName);
                }
                cases.add(buildCase(apiDefinition, params,
                        apiDefinition.getName() + "-必填缺失(" + fieldName + ")",
                        "RequiredFieldStrategy: 移除 body 必填字段 " + fieldName));
            }
        }
        return cases;
    }

    private void removeTopLevel(RequestParams params, String in, String name) {
        switch (in) {
            case "path" -> params.getPath().remove(name);
            case "query" -> params.getQuery().remove(name);
            case "header" -> params.getHeaders().remove(name);
            default -> { }
        }
    }

    private GeneratedCase buildCase(ApiDefinition apiDefinition, RequestParams params, String name, String note) {
        return GeneratedCase.builder()
                .name(name)
                .requestParams(params)
                .assertionRules(List.of(AssertionRule.statusCode(400)))
                .generationNote(note)
                .strategyType(StrategyType.REQUIRED_MISSING)
                .build();
    }
}
