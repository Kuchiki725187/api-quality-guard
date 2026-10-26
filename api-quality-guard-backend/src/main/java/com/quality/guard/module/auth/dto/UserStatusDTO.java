package com.quality.guard.module.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusDTO {

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
