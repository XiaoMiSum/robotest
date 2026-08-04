package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantSendReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfirmReqDTO;
import io.github.xiaomisum.robotest.service.ai.AiAssistantChatService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 全局智能助手对话接口（详细设计 3.2 / 3.3）。
 *
 * <p>上下文标识（workspaceId）经 X-Active-Workspace 头传递（C4），不出现在 URL 或请求体。</p>
 */
@RestController
@RequestMapping("/api/workspace/ai")
public class AiAssistantChatController {

    @Resource
    private AiAssistantChatService aiAssistantChatService;

    /**
     * 发送消息（SSE 流式返回，3.2）
     */
    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @PathVariable("id") UUID conversationId,
            @RequestBody @Valid AiAssistantSendReqDTO reqDTO) {
        return aiAssistantChatService.sendMessage(loginUser.getId(), workspaceId, conversationId, reqDTO);
    }

    /**
     * 确认执行写操作（SSE 流式返回最终答复，3.3.1）
     */
    @PostMapping(value = "/confirmations/approve", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter approve(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestBody @Valid AiConfirmReqDTO reqDTO) {
        return aiAssistantChatService.approve(loginUser.getId(), workspaceId, reqDTO.getConfirmToken());
    }

    /**
     * 取消写操作（3.3.2）
     */
    @PostMapping("/confirmations/cancel")
    public Result<Void> cancel(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestBody @Valid AiConfirmReqDTO reqDTO) {
        aiAssistantChatService.cancel(loginUser.getId(), workspaceId, reqDTO.getConfirmToken());
        return Result.ok();
    }
}
