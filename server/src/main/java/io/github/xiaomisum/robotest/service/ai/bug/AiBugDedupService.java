package io.github.xiaomisum.robotest.service.ai.bug;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugDedupReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugDedupRespDTO;

import java.util.UUID;

/**
 * 缺陷语义查重（US-AI-009，详细设计 3.2/4.2）：Embedding 未配置或失败时自动降级为关键词匹配
 */
public interface AiBugDedupService {

    AiBugDedupRespDTO dedup(UUID userId, UUID workspaceId, UUID projectId, AiBugDedupReqDTO reqDTO);
}
