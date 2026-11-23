package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;

import java.util.UUID;

/**
 * 缺陷分析（详设 3.6 录入时重复检测）：同步向量检索，不落中间表。
 */
public interface BugAnalysisService {

    BugDuplicateCheckRespDTO checkDuplicates(BugDuplicateCheckReqDTO reqDTO, UUID projectId, UUID userId);
}
