package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantMessageRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

/**
 * 助手会话（详设 3.2 ~ 3.4 / 3.6）：按登录用户唯一归属，隔离维度只有归属人，
 * 归属即权限（1000018251，管理员同样不越权）。
 */
public interface AssistantService {

    /** 3.2 会话列表：本人归属 + 标题检索，创建时间倒序，附消息数与最近消息时间聚合 */
    PageResult<AiAssistantConversationRespDTO> pageConversations(AiAssistantConversationPageReqDTO pageReq,
            UUID userId);

    /** 3.3 创建会话：context 引用须属当前用户可见范围（1000018262），context_snapshot 不参与权限判定 */
    AiAssistantConversationRespDTO createConversation(AiAssistantConversationCreateReqDTO reqDTO, UUID userId,
            UUID activeWorkspaceId);

    /** 3.4 重命名 / 归档：部分更新（C11），title 与 status 至少其一，归档动作仅接受 archived */
    AiAssistantConversationRespDTO updateConversation(UUID conversationId, AiAssistantConversationUpdateReqDTO reqDTO,
            UUID userId);

    /** 3.4 删除会话与全部消息（逻辑删除），不影响任何已确认落库的数据与执行审计 */
    boolean deleteConversation(UUID conversationId, UUID userId);

    /** 3.6 消息历史：倒序分页，先做归属校验（1000018251） */
    PageResult<AiAssistantMessageRespDTO> pageMessages(UUID conversationId, AiAssistantMessagePageReqDTO pageReq,
            UUID userId);

    /** 会话归属校验（详设 3.1 防 IDOR）：非本人或不存在一律 1000018251，供发送 / 执行 / 取消复用 */
    AiAssistantConversation getOwnedConversation(UUID conversationId, UUID userId);
}
