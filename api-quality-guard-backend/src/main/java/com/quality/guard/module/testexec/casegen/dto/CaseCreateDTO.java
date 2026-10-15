package com.quality.guard.module.testexec.casegen.dto;

import com.quality.guard.module.testexec.model.AssertionRule;
import com.quality.guard.module.testexec.model.RequestParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 手动创建用例。generationType 由服务端置为 MANUAL，不接受客户端传入。
 */
@Data
public class CaseCreateDTO {

    @NotNull(message = "apiId 不能为空")
    private Long apiId;

    @NotBlank(message = "用例名称不能为空")
    @Size(max = 255, message = "用例名称不能超过 255 字符")
    private String name;

    @NotNull(message = "请求参数不能为空")
    private RequestParams requestParams;

    @NotEmpty(message = "断言规则不能为空")
    private List<@NotNull AssertionRule> assertionRules;
}
