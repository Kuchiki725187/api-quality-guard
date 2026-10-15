package com.quality.guard.module.testexec.casegen;

import com.quality.guard.module.testexec.casegen.engine.TestCaseGenerator;
import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.casegen.strategy.BoundaryValueStrategy;
import com.quality.guard.module.testexec.casegen.strategy.GenerationStrategy;
import com.quality.guard.module.testexec.casegen.strategy.NormalValueStrategy;
import com.quality.guard.module.testexec.casegen.strategy.RequiredFieldStrategy;
import com.quality.guard.module.testexec.casegen.strategy.TypeMismatchStrategy;
import com.quality.guard.module.testexec.casegen.support.BaseParamsFactory;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.model.ParamDef;
import com.quality.guard.module.testexec.model.RequestSchemaDef;
import com.quality.guard.module.testexec.model.ResponseSchemaDef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成引擎单元测试（不依赖 Spring 上下文）。
 * 覆盖：四种策略的输出数量与取值、断言组装、可复现性、策略过滤、空输入。
 */
class TestCaseGeneratorTest {

    private final BaseParamsFactory baseParamsFactory = new BaseParamsFactory();

    private final TestCaseGenerator generator = new TestCaseGenerator(List.of(
            new NormalValueStrategy(baseParamsFactory),
            new RequiredFieldStrategy(baseParamsFactory),
            new TypeMismatchStrategy(baseParamsFactory),
            new BoundaryValueStrategy(baseParamsFactory)));

    // ---------- 测试数据 ----------

    /** createUser 风格：POST /users，body{username, password, age}，201 + code 响应 */
    private ApiDefinition createUserApi() {
        ApiDefinition api = new ApiDefinition();
        api.setName("创建用户");
        api.setHttpMethod("POST");
        api.setPath("/users");
        api.setBaseUrl("http://localhost:9090");

        RequestSchemaDef requestSchema = new RequestSchemaDef();
        requestSchema.setContentType("application/json");
        requestSchema.setParams(List.of(param("body", "body", "object", true,
                map("type", "object",
                        "required", List.of("username", "password"),
                        "properties", map(
                                "username", map("type", "string",
                                        "constraints", map("minLength", 4, "maxLength", 20)),
                                "password", map("type", "string",
                                        "constraints", map("minLength", 6, "maxLength", 32)),
                                "age", map("type", "integer",
                                        "constraints", map("minimum", 0, "maximum", 150)))), null)));
        api.setRequestSchema(requestSchema);
        api.setResponseSchema(responseSchema(201));
        return api;
    }

    /** getUser 风格：GET /users/{id}，path 参数 + query 参数 */
    private ApiDefinition getUserApi() {
        ApiDefinition api = new ApiDefinition();
        api.setName("查询用户");
        api.setHttpMethod("GET");
        api.setPath("/users/{id}");
        api.setBaseUrl("http://localhost:9090");

        RequestSchemaDef requestSchema = new RequestSchemaDef();
        requestSchema.setParams(List.of(
                param("id", "path", "integer", true, null, map("minimum", 1)),
                param("keyword", "query", "string", false, null, map("minLength", 1, "maxLength", 50))));
        api.setRequestSchema(requestSchema);
        api.setResponseSchema(responseSchema(200));
        return api;
    }

    private ParamDef param(String name, String in, String type, boolean required,
                           Map<String, Object> schema, Map<String, Object> constraints) {
        ParamDef param = new ParamDef();
        param.setName(name);
        param.setIn(in);
        param.setType(type);
        param.setRequired(required);
        param.setSchema(schema);
        param.setConstraints(constraints);
        return param;
    }

    private ResponseSchemaDef responseSchema(int statusCode) {
        ResponseSchemaDef response = new ResponseSchemaDef();
        response.setSuccessStatusCode(statusCode);
        response.setSchema(map("type", "object",
                "properties", map("code", map("type", "integer"), "id", map("type", "integer"))));
        return response;
    }

    /** 保序 Map 构建（Map.of 不保证顺序，会影响用例顺序断言） */
    private Map<String, Object> map(Object... kv) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            result.put((String) kv[i], kv[i + 1]);
        }
        return result;
    }

    private GeneratedCase findByName(List<GeneratedCase> cases, String name) {
        return cases.stream().filter(c -> name.equals(c.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("未生成预期用例: " + name));
    }

    // ---------- 测试 ----------

    @Test
    void allStrategiesProduceExpectedCounts() {
        List<GeneratedCase> cases = generator.generate(createUserApi(), null);

        Map<StrategyType, Long> countByType = cases.stream()
                .collect(Collectors.groupingBy(GeneratedCase::getStrategyType, Collectors.counting()));

        assertEquals(1, countByType.get(StrategyType.NORMAL));
        // body 整体必填 1 条 + username/password 两个必填字段各 1 条
        assertEquals(3, countByType.get(StrategyType.REQUIRED_MISSING));
        // body 三个字段各 1 条
        assertEquals(3, countByType.get(StrategyType.TYPE_MISMATCH));
        // username(4) + password(4) + age(4)：每个字段两条合法边界、两条违规边界
        assertEquals(12, countByType.get(StrategyType.BOUNDARY));
        assertEquals(19, cases.size());
    }

    @Test
    void normalCaseCarriesLegalValuesAndAssertions() {
        GeneratedCase normal = findByName(generator.generate(createUserApi(), Set.of(StrategyType.NORMAL)),
                "创建用户-正常值");

        Map<?, ?> body = (Map<?, ?>) normal.getRequestParams().getBody();
        assertEquals("a".repeat(12), body.get("username"));   // 4 + (20-4)/2 = 12
        assertEquals("a".repeat(19), body.get("password"));   // 6 + (32-6)/2 = 19
        assertEquals(75, body.get("age"));                    // 0 + (150-0)/2 = 75

        // 状态码断言 = 文档成功码 201；响应有 code 字段 → 追加业务码断言
        assertEquals(201, normal.getAssertionRules().get(0).getExpected());
        assertEquals("$.code", normal.getAssertionRules().get(1).getPath());
        assertEquals(0, normal.getAssertionRules().get(1).getExpected());
        assertTrue(normal.getGenerationNote().contains("pattern 约束暂未处理"));
    }

    @Test
    void requiredMissingRemovesExactlyOneField() {
        List<GeneratedCase> cases = generator.generate(createUserApi(), Set.of(StrategyType.REQUIRED_MISSING));

        GeneratedCase missingUsername = findByName(cases, "创建用户-必填缺失(username)");
        Map<?, ?> body = (Map<?, ?>) missingUsername.getRequestParams().getBody();
        assertTrue(!body.containsKey("username"));   // 缺目标字段
        assertTrue(body.containsKey("password"));    // 其他字段不受影响
        assertEquals(400, missingUsername.getAssertionRules().get(0).getExpected());

        GeneratedCase missingBody = findByName(cases, "创建用户-必填缺失(body)");
        assertEquals(null, missingBody.getRequestParams().getBody());
    }

    @Test
    void typeMismatchReplacesExactlyOneField() {
        List<GeneratedCase> cases = generator.generate(createUserApi(), Set.of(StrategyType.TYPE_MISMATCH));

        GeneratedCase ageCase = findByName(cases, "创建用户-类型异常(age)");
        Map<?, ?> body = (Map<?, ?>) ageCase.getRequestParams().getBody();
        assertEquals("abc", body.get("age"));                       // 数字字段给不可解析文本
        assertEquals("a".repeat(12), body.get("username"));         // 其他字段保持合法值
        assertEquals(400, ageCase.getAssertionRules().get(0).getExpected());

        GeneratedCase usernameCase = findByName(cases, "创建用户-类型异常(username)");
        Map<?, ?> usernameBody = (Map<?, ?>) usernameCase.getRequestParams().getBody();
        assertTrue(usernameBody.get("username") instanceof Map);    // 字符串字段给复合类型
    }

    @Test
    void boundaryCasesCoverViolationAndLegalEdges() {
        List<GeneratedCase> cases = generator.generate(createUserApi(), Set.of(StrategyType.BOUNDARY));

        GeneratedCase ageMinViolation = findByName(cases, "创建用户-边界值(age=-1)");
        assertEquals(400, ageMinViolation.getAssertionRules().get(0).getExpected());
        assertTrue(ageMinViolation.getGenerationNote().contains("minimum=0"));

        GeneratedCase ageMinLegal = findByName(cases, "创建用户-边界值(age=0)");
        assertEquals(201, ageMinLegal.getAssertionRules().get(0).getExpected());

        GeneratedCase ageMaxViolation = findByName(cases, "创建用户-边界值(age=151)");
        assertEquals(400, ageMaxViolation.getAssertionRules().get(0).getExpected());

        GeneratedCase usernameTooShort = findByName(cases, "创建用户-边界值(username=aaa)");
        assertEquals(400, usernameTooShort.getAssertionRules().get(0).getExpected());
    }

    @Test
    void topLevelParamsAreHandled() {
        List<GeneratedCase> cases = generator.generate(getUserApi(), null);

        Map<StrategyType, Long> countByType = cases.stream()
                .collect(Collectors.groupingBy(GeneratedCase::getStrategyType, Collectors.counting()));
        assertEquals(1, countByType.get(StrategyType.NORMAL));
        // 只有 id 必填；keyword 可选不产出缺失用例
        assertEquals(1, countByType.get(StrategyType.REQUIRED_MISSING));
        assertEquals(2, countByType.get(StrategyType.TYPE_MISMATCH));
        // id: minimum=1 两个候选；keyword: minLength/maxLength 四个候选
        assertEquals(6, countByType.get(StrategyType.BOUNDARY));

        GeneratedCase missingId = findByName(cases, "查询用户-必填缺失(id)");
        Map<String, Object> missingPath = missingId.getRequestParams().getPath();
        assertTrue(missingPath == null || missingPath.get("id") == null);

        GeneratedCase idZero = findByName(cases, "查询用户-边界值(id=0)");
        assertEquals(400, idZero.getAssertionRules().get(0).getExpected());
    }

    @Test
    void generationIsReproducible() {
        List<String> first = generator.generate(createUserApi(), null).stream()
                .map(GeneratedCase::getName).toList();
        List<String> second = generator.generate(createUserApi(), null).stream()
                .map(GeneratedCase::getName).toList();
        assertEquals(first, second);
    }

    @Test
    void strategyFilterKeepsOnlyRequestedTypes() {
        List<GeneratedCase> cases = generator.generate(createUserApi(), Set.of(StrategyType.NORMAL));
        assertEquals(1, cases.size());
        assertEquals(StrategyType.NORMAL, cases.get(0).getStrategyType());
    }

    @Test
    void apiWithoutRequestSchemaYieldsNoCases() {
        ApiDefinition api = new ApiDefinition();
        api.setName("无参数接口");
        assertTrue(generator.generate(api, null).isEmpty());
    }
}
