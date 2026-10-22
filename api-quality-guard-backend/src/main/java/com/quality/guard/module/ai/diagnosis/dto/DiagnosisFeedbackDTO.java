package com.quality.guard.module.ai.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工反馈请求
 */
@Data
public class DiagnosisFeedbackDTO {

    @NotBlank(message = "feedback 不能为空")
    @Pattern(regexp = "CONFIRMED|REJECTED|REVISED",
            message = "feedback 仅支持 CONFIRMED / REJECTED / REVISED")
    private String feedback;

    @Size(max = 1000, message = "说明不能超过 1000 字符")
    private String comment;
}
