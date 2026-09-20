package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConversationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMessageRespDTO;
import io.github.xiaomisum.robotest.service.ai.chat.AiConversationService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

/**
 * 全局智能助手会话管理（详细设计 3.1）。
 *
 * <p>上下文标识（loginUser.getActiveWorkspaceId()）经 X-Active-Workspace 头传递（C4），不出现在 URL 或请求体。</p>
 */
@RestController
@RequestMapping("/api/workspace/ai/conversations")
public class AiConversationController {

    @Resource
    private AiConversationService aiConversationService;

    @GetMapping
    public Result<AiConversationListRespDTO> listConversations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(aiConversationService.listConversations(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), cursor, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AiConversationItemRespDTO> createConversation(
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(aiConversationService.createConversation(loginUser.getId(), loginUser.getActiveWorkspaceId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") UUID conversationId) {
        aiConversationService.deleteConversation(loginUser.getId(), loginUser.getActiveWorkspaceId(), conversationId);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> clearConversations(
            @AuthenticationPrincipal LoginUser loginUser) {
        aiConversationService.clearConversations(loginUser.getId(), loginUser.getActiveWorkspaceId());
        return Result.ok();
    }

    @GetMapping("/{id}/messages")
    public Result<List<AiMessageRespDTO>> listMessages(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") UUID conversationId) {
        return Result.ok(aiConversationService.listMessages(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), conversationId));
    }
}
