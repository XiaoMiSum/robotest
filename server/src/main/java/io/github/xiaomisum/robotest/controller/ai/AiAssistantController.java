package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessageSendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantMessageRespDTO;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantExecuteService;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 智能助手接口（详设 04-ai-assistant）：仅路由 + 参数校验（C2），归属校验在服务层（详设 3.1）。
 *
 * <p>上下文头仅发送与执行环节要求（详设 3.1 表）；本控制器的会话与历史接口只校验 Authorization，
 * 归属即权限（1000018251），管理员同样不越权。</p>
 */
@RestController
@RequestMapping("/api/ai/conversations")
@Tag(name = "AI 助手", description = "会话、消息历史与执行（详设 04-ai-assistant）")
public class AiAssistantController {

    @Resource
    private AssistantService assistantService;
    @Resource
    private AssistantExecuteService assistantExecuteService;

    @GetMapping
    public Result<PageResult<AiAssistantConversationRespDTO>> pageConversations(
            @AuthenticationPrincipal LoginUser loginUser, @Valid AiAssistantConversationPageReqDTO pageReq) {
        return Result.ok(assistantService.pageConversations(pageReq, loginUser.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AiAssistantConversationRespDTO> createConversation(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiAssistantConversationCreateReqDTO reqDTO) {
        return Result.ok(assistantService.createConversation(reqDTO, loginUser.getId(),
                loginUser.getActiveWorkspaceId()));
    }

    @PatchMapping("/{conversationId}")
    public Result<AiAssistantConversationRespDTO> updateConversation(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable UUID conversationId,
            @RequestBody @Valid AiAssistantConversationUpdateReqDTO reqDTO) {
        return Result.ok(assistantService.updateConversation(conversationId, reqDTO, loginUser.getId()));
    }

    @DeleteMapping("/{conversationId}")
    public Result<Boolean> deleteConversation(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID conversationId) {
        return Result.ok(assistantService.deleteConversation(conversationId, loginUser.getId()));
    }

    @GetMapping("/{conversationId}/messages")
    public Result<PageResult<AiAssistantMessageRespDTO>> pageMessages(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable UUID conversationId,
            @Valid AiAssistantMessagePageReqDTO pageReq) {
        return Result.ok(assistantService.pageMessages(conversationId, pageReq, loginUser.getId()));
    }

    @Operation(summary = "发送消息（SSE 流式，详设 3.5）",
            description = "事件流：delta / clarify / preview / citations / done / error，每 15s 心跳注释行；"
                    + "须携带 X-Active-Workspace（4000 字符上限与附件校验在服务层）")
    @ApiResponse(responseCode = "200", description = "SSE 事件流",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string")))
    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID conversationId, @RequestBody @Valid AiAssistantMessageSendReqDTO reqDTO) {
        return assistantService.send(conversationId, reqDTO, loginUser.getId(),
                loginUser.getActiveWorkspaceId());
    }

    @Operation(summary = "确认执行（详设 3.7）",
            description = "按 intent 固化输入逐项落库并返回回执；须携带 X-Active-Workspace 与 X-Active-Project"
                    + "（与 intent.scope 一致），retryIndexes 可选表示对上次失败项单项重试")
    @PostMapping("/{conversationId}/messages/{messageId}/execute")
    public Result<AiAssistantExecuteRespDTO> executeMessage(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID conversationId, @PathVariable UUID messageId,
            @RequestBody(required = false) AiAssistantExecuteReqDTO reqDTO) {
        return Result.ok(assistantExecuteService.execute(conversationId, messageId, reqDTO, loginUser));
    }

    @Operation(summary = "取消预览（详设 3.8）",
            description = "execution 置 rejected（1000018254 防重复），不产生任何业务数据")
    @PostMapping("/{conversationId}/messages/{messageId}/cancel")
    public Result<AiAssistantExecuteRespDTO> cancelMessage(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID conversationId, @PathVariable UUID messageId) {
        return Result.ok(assistantExecuteService.cancel(conversationId, messageId, loginUser));
    }
}
