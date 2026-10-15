package com.quality.guard.module.testexec.casegen.service;

import com.quality.guard.common.result.PageResult;
import com.quality.guard.module.testexec.casegen.dto.CaseCreateDTO;
import com.quality.guard.module.testexec.casegen.dto.CaseGenerateDTO;
import com.quality.guard.module.testexec.casegen.dto.CaseUpdateDTO;
import com.quality.guard.module.testexec.casegen.vo.GenerateResultVO;
import com.quality.guard.module.testexec.casegen.vo.TestCaseVO;

public interface CaseService {

    Long create(CaseCreateDTO dto);

    PageResult<TestCaseVO> page(long page, long pageSize, Long apiId, String generationType, String name);

    TestCaseVO detail(Long id);

    void update(Long id, CaseUpdateDTO dto);

    void delete(Long id);

    /** 规则生成：按策略生成用例并落库（按 apiId + 用例名幂等更新） */
    GenerateResultVO generate(CaseGenerateDTO dto);
}
