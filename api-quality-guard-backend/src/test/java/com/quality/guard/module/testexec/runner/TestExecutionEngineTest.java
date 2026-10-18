package com.quality.guard.module.testexec.runner;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.runner.engine.TestExecutionEngine;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.entity.TestRun;
import com.quality.guard.module.testexec.runner.mapper.TestRunMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行引擎集成测试：
 * 用 JDK 内置 HttpServer 起一个临时被测服务，覆盖 PASSED / FAILED / ERROR 三种终态。
 */
@SpringBootTest
@Transactional
class TestExecutionEngineTest {

    private static HttpServer mockServer;
    private static int mockPort;

    @Autowired
    private TestExecutionEngine testExecutionEngine;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestRunMapper testRunMapper;

    @BeforeAll
    static void startMockServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.createContext("/ok", exchange -> {
            byte[] body = "{\"code\":200,\"message\":\"success\",\"data\":{\"id\":1}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        mockServer.start();
        mockPort = mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    /** 创建一个指向临时被测服务的接口定义 + 用例 */
    private Long createCase(List<AssertionRule> assertionRules) {
        ApiDefinition api = new ApiDefinition();
        api.setName("临时被测接口");
        api.setHttpMethod("GET");
        api.setPath("/ok");
        api.setBaseUrl("http://127.0.0.1:" + mockPort);
        apiDefinitionMapper.insert(api);

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setName("临时被测用例");
        testCase.setRequestParams(new RequestParams());
        testCase.setAssertionRules(assertionRules);
        testCase.setGenerationType("MANUAL");
        testCaseMapper.insert(testCase);
        return testCase.getId();
    }

    @Test
    void passedCasePersistsResultAndRun() {
        Long caseId = createCase(List.of(
                AssertionRule.builder().type("STATUS_CODE").expected(200).build(),
                AssertionRule.builder().type("JSON_FIELD").path("$.code")
                        .operator("EQUALS").expected(200).build()));

        TestResult result = testExecutionEngine.executeCase(caseId, null);

        assertEquals("PASSED", result.getStatus());
        assertNotNull(result.getId());
        assertNotNull(result.getRequestSnapshot());
        assertEquals("GET", result.getRequestSnapshot().getMethod());
        assertTrue(result.getRequestSnapshot().getUrl().startsWith("http://127.0.0.1:" + mockPort));
        assertNotNull(result.getResponseSnapshot());
        assertEquals(200, result.getResponseSnapshot().getStatusCode());
        assertEquals(2, result.getAssertionResults().size());
        assertTrue(result.getAssertionResults().stream().allMatch(AssertionOutcome::isPassed));

        TestRun run = testRunMapper.selectById(result.getRunId());
        assertEquals("SUCCESS", run.getStatus());
        assertEquals(1, run.getSuccessCount());
        assertNotNull(run.getFinishedAt());
    }

    @Test
    void failedAssertionProducesFailedCaseAndRun() {
        Long caseId = createCase(List.of(
                AssertionRule.builder().type("JSON_FIELD").path("$.code")
                        .operator("EQUALS").expected(0).build()));

        TestResult result = testExecutionEngine.executeCase(caseId, null);

        assertEquals("FAILED", result.getStatus());
        assertFalse(result.getAssertionResults().get(0).isPassed());
        TestRun run = testRunMapper.selectById(result.getRunId());
        assertEquals("FAILED", run.getStatus());
        assertEquals(1, run.getFailedCount());
    }

    @Test
    void connectionRefusedProducesError() {
        Long caseId = createCase(List.of(
                AssertionRule.builder().type("STATUS_CODE").expected(200).build()));

        // 请求级地址覆盖：指向本机一个未监听的端口
        TestResult result = testExecutionEngine.executeCase(caseId, "http://127.0.0.1:1");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertNull(result.getResponseSnapshot());
        assertTrue(result.getAssertionResults() == null || result.getAssertionResults().isEmpty());

        TestRun run = testRunMapper.selectById(result.getRunId());
        assertEquals("ERROR", run.getStatus());
        assertEquals(1, run.getErrorCount());
    }

    @Test
    void missingCaseThrows404() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> testExecutionEngine.executeCase(999999L, null));
        assertEquals(404, e.getCode());
    }

    @Test
    void missingBaseUrlThrows400BeforeCreatingRun() {
        ApiDefinition api = new ApiDefinition();
        api.setName("无地址接口");
        api.setHttpMethod("GET");
        api.setPath("/ok");
        // 不设置 baseUrl
        apiDefinitionMapper.insert(api);

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setName("无地址用例");
        testCase.setRequestParams(new RequestParams());
        testCase.setAssertionRules(List.of(
                AssertionRule.builder().type("STATUS_CODE").expected(200).build()));
        testCase.setGenerationType("MANUAL");
        testCaseMapper.insert(testCase);

        BusinessException e = assertThrows(BusinessException.class,
                () -> testExecutionEngine.executeCase(testCase.getId(), null));
        assertEquals(400, e.getCode());
    }
}
