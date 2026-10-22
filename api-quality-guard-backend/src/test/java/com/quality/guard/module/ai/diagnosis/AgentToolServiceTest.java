package com.quality.guard.module.ai.diagnosis;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.module.ai.diagnosis.service.AgentToolService;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 工具接口的数据服务测试（摘要、瘦身、limit 上限）。
 */
@SpringBootTest
@Transactional
class AgentToolServiceTest {

    @Autowired
    private AgentToolService agentToolService;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @Test
    void apiDetailReturnsSummary() {
        ApiDefinition api = new ApiDefinition();
        api.setName("工具测试接口");
        api.setHttpMethod("POST");
        api.setPath("/orders");
        api.setBaseUrl("http://localhost:9090");
        api.setDescription("创建订单");
        apiDefinitionMapper.insert(api);

        Map<String, Object> detail = agentToolService.apiDetail(api.getId());

        assertEquals(api.getId(), detail.get("apiId"));
        assertEquals("POST", detail.get("httpMethod"));
        assertEquals("/orders", detail.get("path"));
    }

    @Test
    void apiDetailOfMissingApiThrows404() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> agentToolService.apiDetail(999999L));
        assertEquals(404, e.getCode());
    }

    @Test
    void caseHistoryReturnsLatestWithFailedAssertionsOnly() {
        ApiDefinition api = new ApiDefinition();
        api.setName("工具历史接口");
        api.setHttpMethod("GET");
        api.setPath("/x");
        apiDefinitionMapper.insert(api);

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setName("工具历史用例");
        testCase.setRequestParams(new RequestParams());
        testCase.setGenerationType("MANUAL");
        testCaseMapper.insert(testCase);

        TestResult passed = new TestResult();
        passed.setRunId(1L);
        passed.setCaseId(testCase.getId());
        passed.setStatus("PASSED");
        passed.setDurationMs(50L);
        testResultMapper.insert(passed);

        TestResult failed = new TestResult();
        failed.setRunId(2L);
        failed.setCaseId(testCase.getId());
        failed.setStatus("FAILED");
        failed.setDurationMs(80L);
        failed.setAssertionResults(List.of(
                AssertionOutcome.builder().type("STATUS_CODE").expected(200).actual(500).passed(false).build(),
                AssertionOutcome.builder().type("JSON_FIELD").path("$.code").expected(0).actual("0").passed(true).build()));
        testResultMapper.insert(failed);

        List<Map<String, Object>> history = agentToolService.caseHistory(testCase.getId(), 5);

        assertEquals(2, history.size());
        // 按 id 倒序：最新的是 failed
        assertEquals("FAILED", history.get(0).get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failedAssertions =
                (List<Map<String, Object>>) history.get(0).get("failedAssertions");
        assertEquals(1, failedAssertions.size());
        assertEquals("STATUS_CODE", failedAssertions.get(0).get("type"));

        // limit 生效
        assertEquals(1, agentToolService.caseHistory(testCase.getId(), 1).size());
        // limit 上限被钳制（传 100 不应报错）
        assertNotNull(agentToolService.caseHistory(testCase.getId(), 100));
        assertTrue(agentToolService.caseHistory(testCase.getId(), 0).size() >= 1);
    }
}
