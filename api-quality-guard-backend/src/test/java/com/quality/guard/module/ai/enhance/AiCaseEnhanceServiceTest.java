package com.quality.guard.module.ai.enhance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.infrastructure.agent.AgentEnhanceResponse;
import com.quality.guard.infrastructure.agent.EnhanceAgentClient;
import com.quality.guard.module.ai.enhance.dto.EnhanceRequestDTO;
import com.quality.guard.module.ai.enhance.model.EnhancedCasePayload;
import com.quality.guard.module.ai.enhance.service.AiCaseEnhanceService;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AI 用例增强服务测试（Agent 客户端用 Mock 替代）。
 */
@SpringBootTest
@Transactional
class AiCaseEnhanceServiceTest {

    @Autowired
    private AiCaseEnhanceService aiCaseEnhanceService;

    @Autowired
    private ApiDefinitionMapper apiDefinitionMapper;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @MockitoBean
    private EnhanceAgentClient enhanceAgentClient;

    private ApiDefinition createApi() {
        ApiDefinition api = new ApiDefinition();
        api.setName("增强测试接口");
        api.setHttpMethod("POST");
        api.setPath("/orders/cancel");
        apiDefinitionMapper.insert(api);
        return api;
    }

    private AgentEnhanceResponse mockResponse() {
        EnhancedCasePayload first = EnhancedCasePayload.builder()
                .name("取消已取消的订单")
                .requestParams(new RequestParams())
                .assertionRules(List.of(
                        AssertionRule.builder().type("STATUS_CODE").expected(400).build()))
                .generationNote("重复取消场景，预期返回业务错误")
                .build();
        EnhancedCasePayload second = EnhancedCasePayload.builder()
                .name("并发取消同一订单")
                .requestParams(new RequestParams())
                .assertionRules(List.of(
                        AssertionRule.builder().type("JSON_FIELD").path("$.code")
                                .operator("EQUALS").expected(0).build()))
                .generationNote("并发场景，预期仅一次成功")
                .build();
        return AgentEnhanceResponse.builder()
                .cases(List.of(first, second))
                .model("glm-5.3-flash")
                .inputTokens(100)
                .outputTokens(80)
                .durationMs(2000)
                .rawOutput("raw")
                .build();
    }

    private long countAiCases(Long apiId) {
        Long count = testCaseMapper.selectCount(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getApiId, apiId)
                .eq(TestCase::getGenerationType, "AI"));
        return count == null ? 0 : count;
    }

    @Test
    void enhancePersistsAiCases() {
        ApiDefinition api = createApi();
        when(enhanceAgentClient.enhance(any())).thenReturn(mockResponse());

        EnhanceRequestDTO dto = new EnhanceRequestDTO();
        dto.setApiId(api.getId());
        dto.setCount(3);
        dto.setExtraContext("同一订单仅可取消一次");

        GenerateResultVO result = aiCaseEnhanceService.enhance(dto);

        assertEquals(2, result.getGeneratedCount());
        assertEquals("AI", result.getCases().get(0).getGenerationType());
        assertEquals(2, countAiCases(api.getId()));

        TestCase saved = testCaseMapper.selectOne(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getApiId, api.getId())
                .eq(TestCase::getName, "取消已取消的订单"));
        assertEquals("AI", saved.getGenerationType());
        assertEquals(1, saved.getAssertionRules().size());
        assertEquals("STATUS_CODE", saved.getAssertionRules().get(0).getType());
        assertEquals(400, saved.getAssertionRules().get(0).getExpected());
        assertEquals("AI 增强: 重复取消场景，预期返回业务错误", saved.getGenerationNote());
    }

    @Test
    void secondEnhanceUpdatesInsteadOfDuplicating() {
        ApiDefinition api = createApi();
        when(enhanceAgentClient.enhance(any())).thenReturn(mockResponse());

        EnhanceRequestDTO dto = new EnhanceRequestDTO();
        dto.setApiId(api.getId());
        aiCaseEnhanceService.enhance(dto);
        aiCaseEnhanceService.enhance(dto);

        assertEquals(2, countAiCases(api.getId()));
    }

    @Test
    void missingApiThrows404() {
        EnhanceRequestDTO dto = new EnhanceRequestDTO();
        dto.setApiId(999999L);
        BusinessException e = assertThrows(BusinessException.class,
                () -> aiCaseEnhanceService.enhance(dto));
        assertEquals(404, e.getCode());
    }

    @Test
    void agentFailurePropagates() {
        ApiDefinition api = createApi();
        when(enhanceAgentClient.enhance(any()))
                .thenThrow(new BusinessException(com.quality.guard.common.result.ResultCode.SERVICE_UNAVAILABLE,
                        "AI 增强服务不可用"));

        EnhanceRequestDTO dto = new EnhanceRequestDTO();
        dto.setApiId(api.getId());
        BusinessException e = assertThrows(BusinessException.class,
                () -> aiCaseEnhanceService.enhance(dto));
        assertEquals(503, e.getCode());
    }
}
