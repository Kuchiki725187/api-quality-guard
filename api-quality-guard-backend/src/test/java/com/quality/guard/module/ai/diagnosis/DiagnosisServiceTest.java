package com.quality.guard.module.ai.diagnosis;

import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.infrastructure.agent.AgentDiagnosisResponse;
import com.quality.guard.infrastructure.agent.DiagnosisAgentClient;
import com.quality.guard.module.ai.diagnosis.dto.DiagnosisFeedbackDTO;
import com.quality.guard.module.ai.diagnosis.entity.AiDiagnosis;
import com.quality.guard.module.ai.diagnosis.mapper.AiDiagnosisMapper;
import com.quality.guard.module.ai.diagnosis.model.DiagnosisCause;
import com.quality.guard.module.ai.diagnosis.model.DiagnosisPayload;
import com.quality.guard.module.ai.diagnosis.service.DiagnosisService;
import com.quality.guard.module.ai.diagnosis.vo.AiDiagnosisVO;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 诊断服务集成测试：Agent 客户端用 Mock 替代（不依赖 Python 服务）。
 */
@SpringBootTest
@Transactional
class DiagnosisServiceTest {

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private AiDiagnosisMapper aiDiagnosisMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestResultMapper testResultMapper;

    @MockitoBean
    private DiagnosisAgentClient diagnosisAgentClient;

    private Long createResult(String status) {
        ApiDefinition api = new ApiDefinition();
        api.setName("诊断测试接口");
        api.setHttpMethod("GET");
        api.setPath("/users/1");
        api.setBaseUrl("http://localhost:9090");
        apiDefinitionMapper.insert(api);

        TestCase testCase = new TestCase();
        testCase.setApiId(api.getId());
        testCase.setName("诊断测试用例");
        testCase.setRequestParams(new RequestParams());
        testCase.setAssertionRules(List.of(
                AssertionRule.builder().type("STATUS_CODE").expected(200).build()));
        testCase.setGenerationType("MANUAL");
        testCaseMapper.insert(testCase);

        TestResult result = new TestResult();
        result.setRunId(1L);
        result.setCaseId(testCase.getId());
        result.setStatus(status);
        result.setDurationMs(120L);
        if ("FAILED".equals(status)) {
            result.setAssertionResults(List.of(AssertionOutcome.builder()
                    .type("STATUS_CODE").expected(200).actual(500).passed(false).build()));
        }
        testResultMapper.insert(result);
        return result.getId();
    }

    private AgentDiagnosisResponse mockResponse() {
        return AgentDiagnosisResponse.builder()
                .diagnosis(DiagnosisPayload.builder()
                        .conclusion("接口返回 500，疑似服务端异常")
                        .causes(List.of(DiagnosisCause.builder()
                                .cause("服务端处理异常")
                                .confidence(0.7)
                                .evidence(List.of("HTTP 500"))
                                .build()))
                        .suggestions(List.of("查看服务端日志"))
                        .build())
                .rounds(2)
                .model("deepseek-chat")
                .inputTokens(100)
                .outputTokens(50)
                .durationMs(1200)
                .rawOutput("raw model output")
                .build();
    }

    @Test
    void diagnosePersistsStructuredOutputAndMeta() {
        Long resultId = createResult("FAILED");
        when(diagnosisAgentClient.diagnose(any())).thenReturn(mockResponse());

        AiDiagnosisVO vo = diagnosisService.diagnose(resultId, "接口刚上线新版本");

        assertNotNull(vo.getId());
        assertEquals(resultId, vo.getTestResultId());
        assertEquals("接口返回 500，疑似服务端异常", vo.getCandidateCauses().getConclusion());
        assertEquals(1, vo.getCandidateCauses().getCauses().size());

        // 数据库回读：JSON 列（candidateCauses）必须能反序列化
        AiDiagnosis saved = aiDiagnosisMapper.selectById(vo.getId());
        assertNotNull(saved.getCandidateCauses());
        assertEquals(0.7, saved.getCandidateCauses().getCauses().get(0).getConfidence());
        assertEquals("deepseek-chat", saved.getModelName());
        assertEquals(100, saved.getInputTokens());
        assertEquals(50, saved.getOutputTokens());
        assertEquals("raw model output", saved.getRawModelOutput());
        assertNotNull(saved.getInputSummary());
        assertTrue(saved.getInputSummary().contains("接口刚上线新版本"));
        assertFalse(saved.getInputSummary().contains("requestSnapshot"));
    }

    @Test
    void passedResultCannotBeDiagnosed() {
        Long resultId = createResult("PASSED");

        BusinessException e = assertThrows(BusinessException.class,
                () -> diagnosisService.diagnose(resultId, null));
        assertEquals(400, e.getCode());
    }

    @Test
    void missingResultThrows404() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> diagnosisService.diagnose(999999L, null));
        assertEquals(404, e.getCode());
    }

    @Test
    void feedbackUpdatesRecord() {
        Long resultId = createResult("FAILED");
        when(diagnosisAgentClient.diagnose(any())).thenReturn(mockResponse());
        AiDiagnosisVO vo = diagnosisService.diagnose(resultId, null);

        DiagnosisFeedbackDTO dto = new DiagnosisFeedbackDTO();
        dto.setFeedback("CONFIRMED");
        dto.setComment("诊断准确");
        diagnosisService.feedback(vo.getId(), dto);

        AiDiagnosis updated = aiDiagnosisMapper.selectById(vo.getId());
        assertEquals("CONFIRMED", updated.getHumanFeedback());
        assertEquals("诊断准确", updated.getHumanComment());
    }
}
