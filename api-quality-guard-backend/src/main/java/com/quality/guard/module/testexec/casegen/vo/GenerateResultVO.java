package com.quality.guard.module.testexec.casegen.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则生成结果：落库后的用例列表
 */
@Data
public class GenerateResultVO {

    private int generatedCount;

    private List<TestCaseVO> cases = new ArrayList<>();
}
