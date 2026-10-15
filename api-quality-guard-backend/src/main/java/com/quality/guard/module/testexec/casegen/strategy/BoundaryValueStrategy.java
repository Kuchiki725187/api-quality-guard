package com.quality.guard.module.testexec.casegen.strategy;

import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.casegen.support.BaseParamsFactory;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.ParamDef;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 边界值策略：对带约束的参数生成边界场景用例
 * constraints 的取值来源见 ParamDef（解析器从 OpenAPI 文档提取：minimum/maximum/minLength/maxLength/pattern/enum）
 */
@Component
@RequiredArgsConstructor
public class BoundaryValueStrategy implements GenerationStrategy {

    private final BaseParamsFactory baseParamsFactory;

    @Override
    public StrategyType getType() {
        return StrategyType.BOUNDARY;
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
                cases.addAll(generateForBodyFields(apiDefinition, param));
            } else if (param.getConstraints() != null && !param.getConstraints().isEmpty()) {
                cases.addAll(generateForParam(apiDefinition, param, param.getConstraints(), null));
            }
        }
        return cases;
    }

    /** body 顶层字段中带约束的（v1 不递归深层结构） */
    private List<GeneratedCase> generateForBodyFields(ApiDefinition apiDefinition, ParamDef bodyParam) {
        List<GeneratedCase> cases = new ArrayList<>();
        Map<String, Object> schema = bodyParam.getSchema();
        if (schema == null || !(schema.get("properties") instanceof Map<?, ?> properties)) {
            return cases;
        }
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> fieldSchemaRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldSchema = (Map<String, Object>) fieldSchemaRaw;
            if (!(fieldSchema.get("constraints") instanceof Map<?, ?> constraintsRaw) || constraintsRaw.isEmpty()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> constraints = (Map<String, Object>) constraintsRaw;
            cases.addAll(generateForParam(apiDefinition, null, constraints, String.valueOf(entry.getKey())));
        }
        return cases;
    }

    /** 对一组约束生成边界用例；fieldName 为 null 表示目标在 body 之外 */
    private List<GeneratedCase> generateForParam(ApiDefinition apiDefinition, ParamDef param,
                                                 Map<String, Object> constraints, String fieldName) {
        List<GeneratedCase> cases = new ArrayList<>();
        String targetName = fieldName != null ? fieldName : param.getName();

        for (BoundaryCandidate candidate : boundaryCandidates(constraints)) {
            RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);
            if (fieldName != null) {
                if (!(params.getBody() instanceof Map<?, ?> bodyRaw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) bodyRaw;
                body.put(fieldName, candidate.value());
            } else {
                putTopLevel(params, param.getIn(), param.getName(), candidate.value());
            }

            int expectedStatus = candidate.legal() ? successStatusCode(apiDefinition) : 400;
            cases.add(GeneratedCase.builder()
                    .name(apiDefinition.getName() + "-边界值(" + targetName + "=" + candidate.value() + ")")
                    .requestParams(params)
                    .assertionRules(List.of(AssertionRule.statusCode(expectedStatus)))
                    .generationNote("BoundaryValueStrategy: " + targetName + " " + candidate.note())
                    .strategyType(StrategyType.BOUNDARY)
                    .build());
        }
        return cases;
    }

    private void putTopLevel(RequestParams params, String in, String name, Object value) {
        switch (in) {
            case "path" -> params.getPath().put(name, value);
            case "query" -> params.getQuery().put(name, value);
            case "header" -> params.getHeaders().put(name, value);
            default -> { }
        }
    }

    private int successStatusCode(ApiDefinition apiDefinition) {
        ResponseSchemaDef response = apiDefinition.getResponseSchema();
        return response != null && response.getSuccessStatusCode() != null
                ? response.getSuccessStatusCode()
                : 200;
    }

    /**
     * 边界候选：value 取值；legal 是否合法（违规值断言 400，合法值断言成功状态码）；
     * note 取值依据说明（写进 generationNote，需带约束值与实际取值）。
     */
    public record BoundaryCandidate(Object value, boolean legal, String note) {
    }

    /** 根据 constraints 推导边界候选列表 */
    private List<BoundaryCandidate> boundaryCandidates(Map<String, Object> constraints) {
        List<BoundaryCandidate> candidates = new ArrayList<>();
        if (constraints == null || constraints.isEmpty()) {
            return candidates;
        }
        integerBoundary(constraints, candidates);
        stringBoundary(constraints, candidates);
        enumBoundary(constraints, candidates);
        return candidates;
    }

    private void enumBoundary(Map<String, Object> constraints, List<BoundaryCandidate> candidates) {
        if (!(constraints.get("enum") instanceof List<?> enumValues) || enumValues.isEmpty()) {
            return;
        }
        // 每个枚举值都是值得验证的合法分支
        for (Object enumValue : enumValues) {
            candidates.add(new BoundaryCandidate(enumValue, true,
                    "枚举值 " + enumValue + "（合法）"));
        }
        // 枚举外的违规值：数字枚举取 -1，其他取一个不可能入清单的字符串
        Object first = enumValues.get(0);
        Object outside = first instanceof Number ? -1 : "NOT_IN_ENUM";
        candidates.add(new BoundaryCandidate(outside, false,
                "取 " + outside + "（不在枚举清单内，违规边界）"));
    }

    private void stringBoundary(Map<String, Object> constraints, List<BoundaryCandidate> candidates) {
        Integer minLen = baseParamsFactory.asInt(constraints, "minLength");
        Integer maxLen = baseParamsFactory.asInt(constraints, "maxLength");
        if (minLen != null) {
            candidates.add(new BoundaryCandidate("a".repeat(minLen), true,
                    "minLength=" + minLen + "，取 " + minLen + " 个字符（合法最小长度）"));
            // 负长度字符串不存在：minLength = 0 时没有下边界违规可测
            if (minLen > 0) {
                candidates.add(new BoundaryCandidate("a".repeat(minLen - 1), false,
                        "minLength=" + minLen + "，取 " + (minLen - 1) + " 个字符（minLength-1，违规边界）"));
            }
        }
        if (maxLen != null) {
            candidates.add(new BoundaryCandidate("a".repeat(maxLen), true,
                    "maxLength=" + maxLen + "，取 " + maxLen + " 个字符（合法最大长度）"));
            candidates.add(new BoundaryCandidate("a".repeat(maxLen + 1), false,
                    "maxLength=" + maxLen + "，取 " + (maxLen + 1) + " 个字符（maxLength+1，违规边界）"));
        }
    }

    private void integerBoundary(Map<String, Object> constraints, List<BoundaryCandidate> candidates) {
        Integer min = baseParamsFactory.asInt(constraints, "minimum");
        Integer max = baseParamsFactory.asInt(constraints, "maximum");
        if (min != null) {
            candidates.add(new BoundaryCandidate(min, true,
                    "minimum=" + min + "（合法最小值）"));
            candidates.add(new BoundaryCandidate(min - 1, false,
                    "minimum=" + min + "，取 " + (min - 1) + "（minimum-1，违规边界）"));
        }
        if (max != null) {
            candidates.add(new BoundaryCandidate(max, true,
                    "maximum=" + max + "（合法最大值）"));
            candidates.add(new BoundaryCandidate(max + 1, false,
                    "maximum=" + max + "，取 " + (max + 1) + "（maximum+1，违规边界）"));
        }
    }
}
