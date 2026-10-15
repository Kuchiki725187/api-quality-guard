package com.quality.guard.module.testexec.casegen.dto;

import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新用例：字段均可选，仅更新非空字段。
 * 手动编辑 AI / RULE 生成的用例时允许修改 requestParams / assertionRules，generationType 保持不变。
 */
@Data
public class CaseUpdateDTO {

    @Size(max = 255, message = "用例名称不能超过 255 字符")
    private String name;

    private RequestParams requestParams;

    private List<AssertionRule> assertionRules;
}
