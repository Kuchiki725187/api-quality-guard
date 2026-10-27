package com.quality.guard.module.ai.assistant.controller;

import com.quality.guard.common.result.Result;
import com.quality.guard.module.ai.assistant.dto.ChatRequestDTO;
import com.quality.guard.module.ai.assistant.service.AssistantService;
import com.quality.guard.module.ai.assistant.vo.ChatAnswerVO;
import com.quality.guard.module.ai.assistant.vo.ChatMessageVO;
import com.quality.guard.module.auth.model.AuthConstants;
import com.quality.guard.module.auth.model.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 助手问答接口（登录后访问，历史按当前用户隔离）
 */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    public Result<ChatAnswerVO> chat(
            @RequestAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE) CurrentUser currentUser,
            @Valid @RequestBody ChatRequestDTO dto) {
        return Result.success(assistantService.chat(currentUser, dto.getMessage()));
    }

    @GetMapping("/history")
    public Result<List<ChatMessageVO>> history(
            @RequestAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE) CurrentUser currentUser) {
        return Result.success(assistantService.history(currentUser));
    }

    @DeleteMapping("/history")
    public Result<Void> clear(
            @RequestAttribute(AuthConstants.CURRENT_USER_ATTRIBUTE) CurrentUser currentUser) {
        assistantService.clear(currentUser);
        return Result.success();
    }
}
