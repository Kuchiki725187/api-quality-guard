package com.quality.guard.module.ai.diagnosis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.infrastructure.agent.AgentDiagnosisRequest;
import com.quality.guard.infrastructure.agent.AgentDiagnosisResponse;
import com.quality.guard.infrastructure.agent.DiagnosisAgentClient;
import com.quality.guard.module.ai.diagnosis.dto.DiagnosisFeedbackDTO;
import com.quality.guard.module.ai.diagnosis.entity.AiDiagnosis;
import com.quality.guard.module.ai.diagnosis.mapper.AiDiagnosisMapper;
import com.quality.guard.module.ai.diagnosis.service.DiagnosisService;
import com.quality.guard.module.ai.diagnosis.vo.AiDiagnosisVO;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import com.quality.guard.module.testexec.runner.entity.TestResult;
import com.quality.guard.module.testexec.runner.mapper.TestResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 诊断服务（主平台侧）
 * 组装诊断上下文 → 调用独立诊断 Agent → 保存 ai_diagnosis（结构化输出与原始输出分开存）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisServiceImpl implements DiagnosisService {

    private static final int MAX_INPUT_SUMMARY_LENGTH = 2000;

    private final AiDiagnosisMapper aiDiagnosisMapper;
    private final TestResultMapper testResultMapper;
    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final DiagnosisAgentClient diagnosisAgentClient;
    private final ObjectMapper objectMapper;
    //执行ai诊断
    @Override
    public AiDiagnosisVO diagnose(Long resultId, String extraContext) {
        TestResult result = testResultMapper.selectById(resultId);
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行结果不存在: " + resultId);
        }
        if ("PASSED".equals(result.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该结果已通过，无需诊断");
        }
        TestCase testCase = testCaseMapper.selectById(result.getCaseId());
        if (testCase == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "关联测试用例不存在（可能已被删除），无法诊断");
        }
        ApiDefinition api = apiDefinitionMapper.selectById(testCase.getApiId());
        if (api == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "关联接口定义不存在（可能已被删除），无法诊断");
        }
        //组装发送给agent的请求体
        AgentDiagnosisRequest request = AgentDiagnosisRequest.builder()
                .caseId(testCase.getId())
                .apiId(api.getId())
                .caseName(testCase.getName())
                .apiMethod(api.getHttpMethod())
                .apiPath(api.getPath())
                .apiBaseUrl(api.getBaseUrl())
                .apiDescription(api.getDescription())
                .apiRequestSchema(api.getRequestSchema())
                .apiResponseSchema(api.getResponseSchema())
                .requestParams(testCase.getRequestParams())
                .requestSnapshot(result.getRequestSnapshot())
                .responseSnapshot(result.getResponseSnapshot())
                .assertionResults(result.getAssertionResults())
                .errorMessage(result.getErrorMessage())
                .extraContext(extraContext)
                .build();

        AgentDiagnosisResponse response = diagnosisAgentClient.diagnose(request);
        //构建诊断记录
        AiDiagnosis entity = new AiDiagnosis();
        entity.setTestResultId(resultId);
        entity.setInputSummary(buildInputSummary(testCase, api, result, extraContext));
        entity.setCandidateCauses(response.getDiagnosis());
        entity.setRawModelOutput(response.getRawOutput());
        entity.setModelName(response.getModel());
        entity.setDurationMs(response.getDurationMs());
        entity.setInputTokens(response.getInputTokens());
        entity.setOutputTokens(response.getOutputTokens());
        aiDiagnosisMapper.insert(entity);

        log.info("AI 诊断完成: resultId={} diagnosisId={} model={}",
                resultId, entity.getId(), response.getModel());
        return toVO(entity, testCase.getName());
    }
     //分页查询ai诊断记录
    @Override
    public PageResult<AiDiagnosisVO> page(long page, long pageSize, Long testResultId, String humanFeedback) {
        Page<AiDiagnosis> result = aiDiagnosisMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<AiDiagnosis>()
                        .eq(testResultId != null, AiDiagnosis::getTestResultId, testResultId)
                        .eq(StringUtils.hasText(humanFeedback), AiDiagnosis::getHumanFeedback, humanFeedback)
                        .orderByDesc(AiDiagnosis::getId));

        Map<Long, Long> resultToCase = loadResultToCase(result.getRecords());
        Map<Long, String> caseNames = loadCaseNames(resultToCase.values());
        //组装诊断记录并返回
        List<AiDiagnosisVO> records = result.getRecords().stream()
                .map(entity -> toVO(entity, caseNames.get(resultToCase.get(entity.getTestResultId()))))
                .toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public AiDiagnosisVO detail(Long id) {
        AiDiagnosis entity = requireById(id);
        String caseName = null;
        TestResult result = testResultMapper.selectById(entity.getTestResultId());
        if (result != null) {
            TestCase testCase = testCaseMapper.selectById(result.getCaseId());
            caseName = testCase == null ? null : testCase.getName();
        }
        return toVO(entity, caseName);
    }
    //设置人工反馈
    @Override
    public void feedback(Long id, DiagnosisFeedbackDTO dto) {
        AiDiagnosis entity = requireById(id);
        entity.setHumanFeedback(dto.getFeedback());
        entity.setHumanComment(dto.getComment());
        aiDiagnosisMapper.updateById(entity);
    }

    private AiDiagnosis requireById(Long id) {
        AiDiagnosis entity = aiDiagnosisMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "诊断记录不存在: " + id);
        }
        return entity;
    }

    /** 输入摘要：保存诊断所需的关键输入（不重复存全量快照），控制长度 */
    private String buildInputSummary(TestCase testCase, ApiDefinition api, TestResult result, String extraContext) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("caseName", testCase.getName());
        summary.put("api", api.getHttpMethod() + " " + api.getPath());
        if (result.getAssertionResults() != null) {
            summary.put("failedAssertions", result.getAssertionResults().stream()
                    .filter(outcome -> !outcome.isPassed())
                    .toList());
        }
        if (result.getErrorMessage() != null) {
            summary.put("errorMessage", result.getErrorMessage());
        }
        if (StringUtils.hasText(extraContext)) {
            summary.put("extraContext", extraContext);
        }
        try {
            String json = objectMapper.writeValueAsString(summary);
            return json.length() <= MAX_INPUT_SUMMARY_LENGTH
                    ? json
                    : json.substring(0, MAX_INPUT_SUMMARY_LENGTH);
        } catch (JsonProcessingException e) {
            return "{\"caseName\":\"" + testCase.getName() + "\"}";
        }
    }
    //加载测试结果
    private Map<Long, Long> loadResultToCase(List<AiDiagnosis> records) {
        //从诊断记录中拿到结果id
        List<Long> resultIds = records.stream()
                .map(AiDiagnosis::getTestResultId)
                .distinct()
                .toList();
        if (resultIds.isEmpty()) {
            return Map.of();
        }
        //根据id查询到测试结果并返回
        return testResultMapper.selectBatchIds(resultIds).stream()
                .collect(Collectors.toMap(TestResult::getId, TestResult::getCaseId));
    }
    //加载用例的名字
    private Map<Long, String> loadCaseNames(java.util.Collection<Long> caseIds) {

        List<Long> distinct = caseIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return testCaseMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(TestCase::getId, TestCase::getName));
    }

    private AiDiagnosisVO toVO(AiDiagnosis entity, String caseName) {
        AiDiagnosisVO vo = new AiDiagnosisVO();
        vo.setId(entity.getId());
        vo.setTestResultId(entity.getTestResultId());
        vo.setCaseName(caseName);
        vo.setInputSummary(entity.getInputSummary());
        vo.setCandidateCauses(entity.getCandidateCauses());
        vo.setRawModelOutput(entity.getRawModelOutput());
        vo.setModelName(entity.getModelName());
        vo.setDurationMs(entity.getDurationMs());
        vo.setInputTokens(entity.getInputTokens());
        vo.setOutputTokens(entity.getOutputTokens());
        vo.setHumanFeedback(entity.getHumanFeedback());
        vo.setHumanComment(entity.getHumanComment());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
