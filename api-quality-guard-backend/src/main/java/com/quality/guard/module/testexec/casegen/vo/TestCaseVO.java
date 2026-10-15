package com.quality.guard.module.testexec.casegen.vo;

import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 测试用例视图对象
 */
@Data
public class TestCaseVO {

    private Long id;

    private Long apiId;

    /** 所属接口名称（查询时填充） */
    private String apiName;

    private String name;

    private RequestParams requestParams;

    private List<AssertionRule> assertionRules;

    private String generationType;

    private String generationNote;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
