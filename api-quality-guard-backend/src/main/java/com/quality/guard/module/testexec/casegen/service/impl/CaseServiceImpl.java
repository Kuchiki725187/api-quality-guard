package com.quality.guard.module.testexec.casegen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quality.guard.common.enums.GenerationType;
import com.quality.guard.common.exception.BusinessException;
import com.quality.guard.common.result.PageResult;
import com.quality.guard.common.result.ResultCode;
import com.quality.guard.module.testexec.casegen.dto.CaseCreateDTO;
import com.quality.guard.module.testexec.casegen.dto.CaseGenerateDTO;
import com.quality.guard.module.testexec.casegen.dto.CaseUpdateDTO;
import com.quality.guard.module.testexec.casegen.engine.TestCaseGenerator;
import com.quality.guard.module.testexec.casegen.entity.TestCase;
import com.quality.guard.module.testexec.casegen.mapper.TestCaseMapper;
import com.quality.guard.module.testexec.casegen.model.GeneratedCase;
import com.quality.guard.module.testexec.casegen.model.StrategyType;
import com.quality.guard.module.testexec.casegen.service.CaseService;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;
import com.quality.guard.module.testexec.casegen.vo.TestCaseVO;
import com.quality.guard.module.testexec.definition.entity.ApiDefinition;
import com.quality.guard.module.testexec.definition.mapper.ApiDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseServiceImpl implements CaseService {

    private final TestCaseMapper testCaseMapper;
    private final ApiDefinitionMapper apiDefinitionMapper;
    private final TestCaseGenerator testCaseGenerator;

    @Override
    public Long create(CaseCreateDTO dto) {
        //先查该接口是否存在
        requireApiDefinition(dto.getApiId());
        TestCase entity = new TestCase();
        entity.setApiId(dto.getApiId());
        entity.setName(dto.getName());
        entity.setRequestParams(dto.getRequestParams());
        entity.setAssertionRules(dto.getAssertionRules());
        entity.setGenerationType(GenerationType.MANUAL.name());
        testCaseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public PageResult<TestCaseVO> page(long page, long pageSize, Long apiId, String generationType, String name) {
        Page<TestCase> result = testCaseMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<TestCase>()
                        .eq(apiId != null, TestCase::getApiId, apiId)
                        .eq(StringUtils.hasText(generationType), TestCase::getGenerationType, generationType)
                        .like(StringUtils.hasText(name), TestCase::getName, name)
                        .orderByDesc(TestCase::getId));
        Map<Long, String> apiNames = loadApiNames(result.getRecords());
        List<TestCaseVO> records = result.getRecords().stream()
                .map(entity -> toVO(entity, apiNames.get(entity.getApiId())))
                .toList();
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public TestCaseVO detail(Long id) {
        TestCase entity = requireById(id);
        ApiDefinition apiDefinition = apiDefinitionMapper.selectById(entity.getApiId());
        return toVO(entity, apiDefinition == null ? null : apiDefinition.getName());
    }

    @Override
    public void update(Long id, CaseUpdateDTO dto) {
        TestCase entity = requireById(id);
        if (StringUtils.hasText(dto.getName())) {
            entity.setName(dto.getName());
        }
        if (dto.getRequestParams() != null) {
            entity.setRequestParams(dto.getRequestParams());
        }
        if (dto.getAssertionRules() != null && !dto.getAssertionRules().isEmpty()) {
            entity.setAssertionRules(dto.getAssertionRules());
        }
        testCaseMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireById(id);
        testCaseMapper.deleteById(id);
    }

    @Override
    public GenerateResultVO generate(CaseGenerateDTO dto) {
        //先拿到接口定义
        ApiDefinition apiDefinition = requireApiDefinition(dto.getApiId());
        //解析接口生成策略
        Set<StrategyType> types = parseStrategies(dto.getStrategies());
        //生成测试用例
        List<GeneratedCase> generated = testCaseGenerator.generate(apiDefinition, types);

        GenerateResultVO result = new GenerateResultVO();
        List<TestCaseVO> cases = new ArrayList<>();
        //每个生成的实例转换为TestCase入库
        for (GeneratedCase generatedCase : generated) {
            TestCase entity = upsertRuleCase(apiDefinition.getId(), generatedCase);
            cases.add(toVO(entity, apiDefinition.getName()));
        }
        result.setGeneratedCount(cases.size());
        result.setCases(cases);
        return result;
    }

    /** 按 apiId + 用例名幂等落库：已存在则更新参数/断言/说明（保留原 ID），否则新增 */
    private TestCase upsertRuleCase(Long apiId, GeneratedCase generatedCase) {
        TestCase existing = testCaseMapper.selectOne(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getApiId, apiId)
                .eq(TestCase::getName, generatedCase.getName())
                .eq(TestCase::getGenerationType, GenerationType.RULE.name()), false);
        if (existing != null) {
            existing.setRequestParams(generatedCase.getRequestParams());
            existing.setAssertionRules(generatedCase.getAssertionRules());
            existing.setGenerationNote(generatedCase.getGenerationNote());
            testCaseMapper.updateById(existing);
            return existing;
        }
        //不存在新建再入库
        TestCase entity = new TestCase();
        entity.setApiId(apiId);
        entity.setName(generatedCase.getName());
        entity.setRequestParams(generatedCase.getRequestParams());
        entity.setAssertionRules(generatedCase.getAssertionRules());
        entity.setGenerationType(GenerationType.RULE.name());
        entity.setGenerationNote(generatedCase.getGenerationNote());
        testCaseMapper.insert(entity);
        return entity;
    }
    //解析生成策略
    private Set<StrategyType> parseStrategies(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<StrategyType> types = EnumSet.noneOf(StrategyType.class);
        for (String name : names) {
            try {
                types.add(StrategyType.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "非法的生成策略: " + name);
            }
        }
        return types;
    }
    //查看接口是否存在
    private ApiDefinition requireApiDefinition(Long apiId) {
        ApiDefinition apiDefinition = apiDefinitionMapper.selectById(apiId);
        if (apiDefinition == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "接口定义不存在: " + apiId);
        }
        return apiDefinition;
    }
    //查看测试用例是否已存在
    private TestCase requireById(Long id) {
        TestCase entity = testCaseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "测试用例不存在: " + id);
        }
        return entity;
    }

    private Map<Long, String> loadApiNames(List<TestCase> cases) {
        List<Long> apiIds = cases.stream().map(TestCase::getApiId).distinct().toList();
        if (apiIds.isEmpty()) {
            return Map.of();
        }
        return apiDefinitionMapper.selectBatchIds(apiIds).stream()
                .collect(Collectors.toMap(ApiDefinition::getId, ApiDefinition::getName));
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
