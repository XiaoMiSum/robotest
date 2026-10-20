package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugDraftSuggestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;

import java.util.UUID;

/**
 * 新建缺陷实时建议（详设 3.5）：基于标题与已填内容单跳模型调用，
 * 不产生任务、不落库，建议仅以徽标呈现、用户值优先。
 */
public interface BugDraftSuggestService {

    BugDraftSuggestRespDTO suggest(BugDraftSuggestReqDTO reqDTO, UUID projectId, UUID userId);
}
