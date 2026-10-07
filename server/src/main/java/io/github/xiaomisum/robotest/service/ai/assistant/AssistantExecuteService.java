package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantExecuteRespDTO;

import java.util.UUID;

/**
 * 助手确认执行与取消（详设 3.7 / 3.8）：执行按 intent 固化输入逐项落库，
 * 回执写 execution 后不可变更（重试只追加），取消仅置 rejected、不产生业务数据。
 */
public interface AssistantExecuteService {

    /**
     * 确认执行（详设 3.7）：校验链 251 → 252 → 255 → 253 → 254 分档 → 256 → 257 →
     * 结构校验后条件认领 execution，逐项独立事务落库并写回执；retryIndexes 非空为
     * 对上次失败项按 seq 映射单项重试（追加 results，不改既有条目）。
     */
    AiAssistantExecuteRespDTO execute(UUID conversationId, UUID messageId,
            AiAssistantExecuteReqDTO reqDTO, LoginUser loginUser);

    /** 取消预览（详设 3.8）：251 → 252 → 255 → 254 后条件认领 rejected，不校验时效与作用域 */
    AiAssistantExecuteRespDTO cancel(UUID conversationId, UUID messageId, LoginUser loginUser);
}
