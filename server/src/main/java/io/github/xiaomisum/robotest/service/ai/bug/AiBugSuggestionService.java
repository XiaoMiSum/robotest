package io.github.xiaomisum.robotest.service.ai.bug;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugSuggestionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugSuggestionRespDTO;

import java.util.UUID;

/**
 * 缺陷表单智能建议：同步 LLM 调用，结果仅回填表单待用户确认
 */
public interface AiBugSuggestionService {

    AiBugSuggestionRespDTO suggest(UUID userId, UUID workspaceId, UUID projectId, AiBugSuggestionReqDTO reqDTO);
}
