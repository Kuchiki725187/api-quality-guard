package com.quality.guard.module.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequestDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最长 2000 个字符")
    private String message;
}
