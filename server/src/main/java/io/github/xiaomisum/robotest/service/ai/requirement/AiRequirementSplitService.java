package io.github.xiaomisum.robotest.service.ai.requirement;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiRequirementSplitReqDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * AI 需求文档拆分（US-AI-019，3.2.3）：整份需求文档按模块/功能拆分为细粒度需求条目，
 * SSE 流式返回（done 帧为模块分组结构，纯预览不落库）。
 */
public interface AiRequirementSplitService {

    /**
     * 拆分需求文档（SSE：delta 透传 + done 帧携带 modules 分组与 warnings）
     */
    SseEmitter split(UUID userId, UUID workspaceId, UUID projectId, AiRequirementSplitReqDTO reqDTO);
}
