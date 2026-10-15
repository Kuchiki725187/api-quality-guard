package com.quality.guard.module.testexec.casegen.support;

import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.model.ParamDef;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合法参数值工厂：四个生成策略的公共底座
 * - pattern / format 约束暂不处理
 */
@Component
public class BaseParamsFactory {

    public RequestParams buildLegalParams(ApiDefinition apiDefinition) {
        RequestParams params = new RequestParams();
        Map<String, Object> path = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        //先读取请求的内容和参数
        RequestSchemaDef requestSchema = apiDefinition.getRequestSchema();
        if (requestSchema != null && requestSchema.getParams() != null) {
            //循环遍历每个参数生成合法值并放入集合中
            for (ParamDef param : requestSchema.getParams()) {
                if (param.getIn() == null) {
                    continue;
                }
                switch (param.getIn()) {
                    case "path" -> path.put(param.getName(), legalScalarValue(param.getType(), param.getConstraints()));
                    case "query" -> query.put(param.getName(), legalScalarValue(param.getType(), param.getConstraints()));
                    case "header" -> headers.put(param.getName(), legalScalarValue(param.getType(), param.getConstraints()));
                    case "body" -> params.setBody(buildObjectFromSchema(param.getSchema()));
                    default -> { }
                }
            }
        }

        params.setPath(path.isEmpty() ? null : path);
        params.setQuery(query.isEmpty() ? null : query);
        params.setHeaders(headers.isEmpty() ? null : headers);
        return params;
    }

    /** 按类型与约束构造一个合法标量值 */
    public Object legalScalarValue(String type, Map<String, Object> constraints) {
        if (type == null) {
            return "test";
        }
        return switch (type) {
            case "integer" -> legalInteger(constraints);
            case "number" -> legalNumber(constraints);
            case "string" -> legalString(constraints);
            case "boolean" -> true;
            case "array" -> new ArrayList<>();
            default -> "test";
        };
    }
    @SuppressWarnings("unchecked")
    //生成对象并遍历对象中的字段
    public Map<String, Object> buildObjectFromSchema(Map<String, Object> schema) {
        //创建结果map
        Map<String, Object> result = new LinkedHashMap<>();
        //直到schema 为 null 或没有 properties 时，说明对象不会再往下展开了，停止递归
        //判断是不是对象
        if (schema == null || !(schema.get("properties") instanceof Map<?, ?> properties)) {
            return result;
        }
        //遍历对象所有字段并交给valueFromSchema
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            //判断字段是否为schema，因为字段本身也是map
            if (entry.getValue() instanceof Map<?, ?> fieldSchema) {
                result.put(String.valueOf(entry.getKey()), valueFromSchema((Map<String, Object>) fieldSchema));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    //根据字段类型生成对应数据
    private Object valueFromSchema(Map<String, Object> schema) {
        //先从schema中取出类型字段，转换为string类型方便比较
        String type = schema.get("type") instanceof String t ? t : null;
        //如果是object类型的对象，交给buildObjectFromSchema进行递归
        if ("object".equals(type)) {
            return buildObjectFromSchema(schema);
        }
        //array类型的对象，获取数组中的元素类型，递归生成数据
        if ("array".equals(type)) {
            if (schema.get("items") instanceof Map<?, ?> items) {
                return List.of(valueFromSchema((Map<String, Object>) items));
            }
            return List.of();
        }
        //从schema中拿到约束集合，走到这时字段类型为普通字段，根据约束条件生成数据
        Map<String, Object> constraints = schema.get("constraints") instanceof Map<?, ?> c
                ? (Map<String, Object>) c
                : null;
        return legalScalarValue(type, constraints);
    }

    private Integer legalInteger(Map<String, Object> constraints) {
        if (enumFirst(constraints) instanceof Number number) {
            return number.intValue();
        }
        Integer min = asInt(constraints, "minimum");
        Integer max = asInt(constraints, "maximum");
        if (min != null && max != null) {
            return min + (max - min) / 2;
        }
        if (min != null) {
            return min + 1;
        }
        if (max != null) {
            return max - 1;
        }
        return 1;
    }

    private Double legalNumber(Map<String, Object> constraints) {
        if (enumFirst(constraints) instanceof Number number) {
            return number.doubleValue();
        }
        Double min = asDouble(constraints, "minimum");
        Double max = asDouble(constraints, "maximum");
        if (min != null && max != null) {
            return min + (max - min) / 2;
        }
        if (min != null) {
            return min + 1;
        }
        if (max != null) {
            return max - 1;
        }
        return 1.0;
    }

    private String legalString(Map<String, Object> constraints) {
        if (enumFirst(constraints) != null) {
            return String.valueOf(enumFirst(constraints));
        }
        Integer minLen = asInt(constraints, "minLength");
        Integer maxLen = asInt(constraints, "maxLength");
        int length;
        if (minLen != null && maxLen != null) {
            length = minLen + (maxLen - minLen) / 2;
        } else if (minLen != null) {
            length = minLen;
        } else if (maxLen != null) {
            length = Math.min(maxLen, 8);
        } else {
            length = 8;
        }
        return "a".repeat(Math.max(length, 1));
    }

    private Object enumFirst(Map<String, Object> constraints) {
        if (constraints != null && constraints.get("enum") instanceof List<?> enums && !enums.isEmpty()) {
            return enums.get(0);
        }
        return null;
    }

    /**
     * constraints 的值来自 JSON 反序列化，运行时类型是 Integer/Long/BigDecimal 等 Number
     * 不能用 (int) 强转，必须经 Number 取值
     */
    public Integer asInt(Map<String, Object> constraints, String key) {
        Object value = constraints == null ? null : constraints.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    public Double asDouble(Map<String, Object> constraints, String key) {
        Object value = constraints == null ? null : constraints.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
