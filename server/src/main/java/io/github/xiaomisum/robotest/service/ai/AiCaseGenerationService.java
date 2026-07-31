package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 脑图 AI 生成类功能（US-AI-001 用例子树生成）：归属校验 + 上下文组装 + 网关流式调用
 */
public interface AiCaseGenerationService {

    /**
     * 生成用例子树（SSE：delta 透传 + done 帧携带结构化用例树与 warnings）
     */
    SseEmitter generateCaseTree(UUID userId, UUID workspaceId, UUID projectId, AiCaseGenerateReqDTO reqDTO);
}
