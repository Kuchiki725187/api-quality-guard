package com.quality.guard.module.ai.enhance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quality.guard.common.enums.GenerationType;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.infrastructure.agent.AgentEnhanceRequest;
import com.quality.guard.infrastructure.agent.AgentEnhanceResponse;
import com.quality.guard.infrastructure.agent.EnhanceAgentClient;
import com.quality.guard.module.ai.enhance.dto.EnhanceRequestDTO;
import com.quality.guard.module.ai.enhance.model.EnhancedCasePayload;
import com.quality.guard.module.ai.enhance.service.AiCaseEnhanceService;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;
import com.quality.guard.module.testexec.casegen.vo.TestCaseVO;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.RequestParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 用例增强服务：Agent 生成候选用例 → 主平台校验并落库（generationType=AI）
 * AI 用例与规则用例按 apiId + 用例名分别幂等，互不覆盖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCaseEnhanceServiceImpl implements AiCaseEnhanceService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_NOTE_LENGTH = 500;

    private final ApiDefinitionMapper apiDefinitionMapper;
    private final TestCaseMapper testCaseMapper;
    private final EnhanceAgentClient enhanceAgentClient;

    @Override
    public GenerateResultVO enhance(EnhanceRequestDTO dto) {
        ApiDefinition api = apiDefinitionMapper.selectById(dto.getApiId());
        if (api == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接口定义不存在: " + dto.getApiId());
        }

        AgentEnhanceRequest request = AgentEnhanceRequest.builder()
                .apiId(api.getId())
                .apiName(api.getName())
                .apiMethod(api.getHttpMethod())
                .apiPath(api.getPath())
                .apiDescription(api.getDescription())
                .apiRequestSchema(api.getRequestSchema())
                .apiResponseSchema(api.getResponseSchema())
                .extraContext(dto.getExtraContext())
                .count(dto.getCount() == null ? 3 : dto.getCount())
                .build();

        AgentEnhanceResponse response = enhanceAgentClient.enhance(request);

        List<TestCaseVO> cases = new ArrayList<>();
        for (EnhancedCasePayload payload : response.getCases()) {
            validate(payload);
            TestCase entity = upsertAiCase(api, payload);
            cases.add(toVO(entity, api.getName()));
        }

        log.info("AI 用例增强完成: apiId={} generated={} model={}",
                api.getId(), cases.size(), response.getModel());
        GenerateResultVO result = new GenerateResultVO();
        result.setGeneratedCount(cases.size());
        result.setCases(cases);
        return result;
    }

    private void validate(EnhancedCasePayload payload) {
        if (payload.getName() == null || payload.getName().isBlank()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI 返回的用例缺少名称");
        }
        if (payload.getAssertionRules() == null || payload.getAssertionRules().isEmpty()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE,
                    "AI 返回的用例缺少断言规则: " + payload.getName());
        }
    }

    /** 按 apiId + 用例名幂等（仅 AI 来源），已存在则更新参数/断言/说明并保留原 ID */
    private TestCase upsertAiCase(ApiDefinition api, EnhancedCasePayload payload) {
        TestCase existing = testCaseMapper.selectOne(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getApiId, api.getId())
                .eq(TestCase::getName, payload.getName())
                .eq(TestCase::getGenerationType, GenerationType.AI.name()), false);

        RequestParams params = payload.getRequestParams() == null ? new RequestParams() : payload.getRequestParams();
        String note = payload.getGenerationNote() == null
                ? "AI 增强生成"
                : "AI 增强: " + payload.getGenerationNote();

        if (existing != null) {
            existing.setRequestParams(params);
            existing.setAssertionRules(payload.getAssertionRules());
            existing.setGenerationNote(truncate(note, MAX_NOTE_LENGTH));
            testCaseMapper.updateById(existing);
            return existing;
        }

        TestCase entity = new TestCase();
        entity.setApiId(api.getId());
        entity.setName(truncate(payload.getName(), MAX_NAME_LENGTH));
        entity.setRequestParams(params);
        entity.setAssertionRules(payload.getAssertionRules());
        entity.setGenerationType(GenerationType.AI.name());
        entity.setGenerationNote(truncate(note, MAX_NOTE_LENGTH));
        testCaseMapper.insert(entity);
        return entity;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private TestCaseVO toVO(TestCase entity, String apiName) {
        TestCaseVO vo = new TestCaseVO();
        vo.setId(entity.getId());
        vo.setApiId(entity.getApiId());
        vo.setApiName(apiName);
        vo.setName(entity.getName());
        vo.setRequestParams(entity.getRequestParams());
        vo.setAssertionRules(entity.getAssertionRules());
        vo.setGenerationType(entity.getGenerationType());
        vo.setGenerationNote(entity.getGenerationNote());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
