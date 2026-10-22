package com.quality.guard.module.ai.diagnosis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.ai.diagnosis.service.AgentToolService;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.model.AssertionOutcome;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具数据服务：返回摘要而非全量快照，控制进入模型上下文的数据量
 */
@Service
@RequiredArgsConstructor
public class AgentToolServiceImpl implements AgentToolService {

    private static final int MAX_HISTORY_LIMIT = 10;

    private final ApiDefinitionMapper apiDefinitionMapper;
    private final TestResultMapper testResultMapper;
    //查询api定义并整理出所需要的数据
    @Override
    public Map<String, Object> apiDetail(Long apiId) {
        ApiDefinition api = apiDefinitionMapper.selectById(apiId);
        if (api == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接口定义不存在: " + apiId);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("apiId", api.getId());
        detail.put("name", api.getName());
        detail.put("httpMethod", api.getHttpMethod());
        detail.put("path", api.getPath());
        detail.put("baseUrl", api.getBaseUrl());
        detail.put("description", api.getDescription());
        detail.put("requestSchema", api.getRequestSchema());
        detail.put("responseSchema", api.getResponseSchema());
        return detail;
    }
    //查询测试用例的历史记录
    @Override
    public List<Map<String, Object>> caseHistory(Long caseId, int limit) {
        //将传递的值限制在指定范围
        int safeLimit = Math.clamp(limit, 1, MAX_HISTORY_LIMIT);
        Page<TestResult> page = testResultMapper.selectPage(new Page<>(1, safeLimit),
                new LambdaQueryWrapper<TestResult>()
                        .eq(TestResult::getCaseId, caseId)
                        .orderByDesc(TestResult::getId));
        //将实体转换成简化的数据
        return page.getRecords().stream()
                .map(this::toHistoryItem)
                .toList();
    }
    //将测试结果转换成简化的历史记录给ai提供证据
    private Map<String, Object> toHistoryItem(TestResult result) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("resultId", result.getId());
        item.put("status", result.getStatus());
        item.put("durationMs", result.getDurationMs());
        item.put("errorMessage", result.getErrorMessage());
        item.put("failedAssertions", failedAssertions(result.getAssertionResults()));
        LocalDateTime createdAt = result.getCreatedAt();
        item.put("createdAt", createdAt == null ? null : createdAt.toString());
        return item;
    }
    //筛选失败的断言，只将失败的断言传递给agent
    private List<Map<String, Object>> failedAssertions(List<AssertionOutcome> outcomes) {
        if (outcomes == null) {
            return List.of();
        }
        return outcomes.stream()
                .filter(outcome -> !outcome.isPassed())
                .map(outcome -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", outcome.getType());
                    item.put("path", outcome.getPath());
                    item.put("operator", outcome.getOperator());
                    item.put("expected", outcome.getExpected());
                    item.put("actual", outcome.getActual());
                    item.put("message", outcome.getMessage());
                    return item;
                })
                .toList();
    }
}
