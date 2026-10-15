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
 * 类型异常策略：为参数生成"传入错误类型值"的用例
 */
@Component
@RequiredArgsConstructor
public class TypeMismatchStrategy implements GenerationStrategy {

    private final BaseParamsFactory baseParamsFactory;

    @Override
    public StrategyType getType() {
        return StrategyType.TYPE_MISMATCH;
    }

    @Override
    public List<GeneratedCase> generate(ApiDefinition apiDefinition) {
        //拿到接口请求对象
        RequestSchemaDef requestSchema = apiDefinition.getRequestSchema();
        if (requestSchema == null || requestSchema.getParams() == null) {
            return List.of();
        }
        //创建候选测试用例集合
        List<GeneratedCase> cases = new ArrayList<>();
        for (ParamDef param : requestSchema.getParams()) {
            if ("body".equals(param.getIn())) {
                cases.addAll(generateForBodyFields(apiDefinition, param));
            } else if (param.getIn() != null && param.getType() != null) {
                //创建合法参数值，params为创建的请求参数，param为读取到的请求参数信息
                RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);
                //生成类型错误的值
                Object badValue = wrongTypeValue(param.getType());
                //将错误类型的值组装进创建的请求参数
                putTopLevel(params, param.getIn(), param.getName(), badValue);
                cases.add(buildCase(apiDefinition, params,
                        apiDefinition.getName() + "-类型异常(" + param.getName() + ")",
                        "TypeMismatchStrategy: " + param.getName() + " 声明为 " + param.getType()
                                + "，传入 " + describe(badValue)));
            }
        }
        return cases;
    }

    //制造顶层字段的类型异常，嵌套的字段不生成，所以不用递归（v1）
    private List<GeneratedCase> generateForBodyFields(ApiDefinition apiDefinition, ParamDef bodyParam) {
        List<GeneratedCase> cases = new ArrayList<>();
        //拿到请求体结构
        Map<String, Object> schema = bodyParam.getSchema();
        //判断是不是对象，只有对象才有properties
        if (schema == null || !(schema.get("properties") instanceof Map<?, ?> properties)) {
            return cases;
        }
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            //判断字段定义是不是map
            if (!(entry.getValue() instanceof Map<?, ?> fieldSchemaRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
              //类型转换，将map的key和value转换为确定类型
            Map<String, Object> fieldSchema = (Map<String, Object>) fieldSchemaRaw;
            //获取字段类型fieldType
            if (!(fieldSchema.get("type") instanceof String fieldType)) {
                continue;
            }
            //获取字段名字
            String fieldName = String.valueOf(entry.getKey());
            //生成合法的请求参数
            RequestParams params = baseParamsFactory.buildLegalParams(apiDefinition);
             //拿到body类型数据
            if (!(params.getBody() instanceof Map<?, ?> bodyRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
             //强转为map
            Map<String, Object> body = (Map<String, Object>) bodyRaw;
            //生成错误类型数据
            Object badValue = wrongTypeValue(fieldType);
            //替换原来正确的字段
            body.put(fieldName, badValue);
            //创建测试案例并加入到候选列表
            cases.add(buildCase(apiDefinition, params,
                    apiDefinition.getName() + "-类型异常(" + fieldName + ")",
                    "TypeMismatchStrategy: " + fieldName + " 声明为 " + fieldType
                            + "，传入 " + describe(badValue)));
        }
        return cases;
    }
     //组装请求参数
    private void putTopLevel(RequestParams params, String in, String name, Object value) {
        switch (in) {
            case "path" -> params.getPath().put(name, value);
            case "query" -> params.getQuery().put(name, value);
            case "header" -> params.getHeaders().put(name, value);
            default -> { }
        }
    }

    private GeneratedCase buildCase(ApiDefinition apiDefinition, RequestParams params, String name, String note) {
        return GeneratedCase.builder()
                .name(name)
                .requestParams(params)
                .assertionRules(List.of(AssertionRule.statusCode(400)))
                .generationNote(note)
                .strategyType(StrategyType.TYPE_MISMATCH)
                .build();
    }

    private String describe(Object value) {
        return value instanceof String s ? "\"" + s + "\"" : String.valueOf(value);
    }

    /**
     * 按声明类型返回一个"错误类型"的值
     */
    private Object wrongTypeValue(String declaredType) {
        return switch (declaredType) {
            case "string" -> Map.of("bad", declaredType);
            case "integer", "number" -> "abc";
            case "boolean" -> "yes";
            case "array" -> 12345;
            case "object" -> "not-an-object";
            default -> "invalid";
        };
    }
}
