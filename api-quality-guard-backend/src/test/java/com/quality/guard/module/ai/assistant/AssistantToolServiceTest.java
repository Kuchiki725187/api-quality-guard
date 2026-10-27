package com.quality.guard.module.ai.assistant;

import com.quality.guard.module.ai.assistant.tool.AssistantToolService;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 助手工具数据测试：摘要结构、limit 限制、失败断言格式化。
 */
@SpringBootTest
@Transactional
class AssistantToolServiceTest {

    @Autowired
    private AssistantToolService assistantToolService;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @Test
    void searchApisReturnsSummariesAndClampsLimit() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        ApiDefinition api = createApi("IT-Assistant-" + suffix, "GET", "/it/assistant/" + suffix);

        List<Map<String, Object>> found = assistantToolService.searchApis(suffix, 99);

        assertEquals(1, found.size());
        assertEquals(api.getId(), found.get(0).get("apiId"));
        assertEquals("GET", found.get(0).get("method"));
        assertEquals("/it/assistant/" + suffix, found.get(0).get("path"));

        List<Map<String, Object>> latest = assistantToolService.searchApis("", 1);
        assertEquals(1, latest.size());
    }

    @Test
    void recentFailuresIncludesContextAndFailedAssertion() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        ApiDefinition api = createApi("IT-Failure-" + suffix, "POST", "/it/failure/" + suffix);

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setName("IT-失败用例-" + suffix);
        testCase.setGenerationType("MANUAL");
        testCaseMapper.insert(testCase);

        TestResult failed = new TestResult();
        failed.setRunId(1L);
        failed.setCaseId(testCase.getId());
        failed.setStatus("FAILED");
        failed.setDurationMs(120L);
        failed.setAssertionResults(List.of(AssertionOutcome.builder()
                .type("STATUS_CODE")
                .expected(200)
                .actual(500)
                .passed(false)
                .build()));
        testResultMapper.insert(failed);

        TestResult error = new TestResult();
        error.setRunId(1L);
        error.setCaseId(testCase.getId());
        error.setStatus("ERROR");
        error.setErrorMessage("连接被拒绝");
        testResultMapper.insert(error);

        List<Map<String, Object>> failures = assistantToolService.recentFailures(2);

        assertEquals(2, failures.size());
        Map<String, Object> first = failures.get(0);
        assertEquals(error.getId(), first.get("resultId"));
        assertEquals("ERROR", first.get("status"));
        assertEquals("连接被拒绝", first.get("errorMessage"));
        assertNull(first.get("failedAssertion"));

        Map<String, Object> second = failures.get(1);
        assertEquals(failed.getId(), second.get("resultId"));
        assertEquals("FAILED", second.get("status"));
        assertEquals(api.getId(), second.get("apiId"));
        assertEquals(testCase.getName(), second.get("caseName"));
        assertNotNull(second.get("failedAssertion"));
        assertTrue(second.get("failedAssertion").toString().contains("HTTP 状态码"));
    }

    @Test
    void platformReportContainsOverviewAndQuality() {
        Map<String, Object> report = assistantToolService.platformReport();

        assertNotNull(report.get("overview"));
        assertNotNull(report.get("quality"));
    }

    private ApiDefinition createApi(String name, String method, String path) {
        ApiDefinition api = new ApiDefinition();
        api.setName(name);
        api.setHttpMethod(method);
        api.setPath(path);
        api.setDescription("IT 工具测试");
        apiDefinitionMapper.insert(api);
        return api;
    }
}
