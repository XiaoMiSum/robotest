package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;

import java.util.UUID;

/**
 * 缺陷分析（详设 3.2 趋势 / 3.3 度量 / 3.8 录入重复检测）：统计实时计算，不落中间表。
 */
public interface BugAnalysisService {

    BugTrendsRespDTO trends(BugAnalysisQueryReqDTO query, UUID projectId, UUID userId);

    BugMetricsRespDTO metrics(BugAnalysisQueryReqDTO query, UUID projectId, UUID userId);

    BugDuplicateCheckRespDTO checkDuplicates(BugDuplicateCheckReqDTO reqDTO, UUID projectId, UUID userId);
}
