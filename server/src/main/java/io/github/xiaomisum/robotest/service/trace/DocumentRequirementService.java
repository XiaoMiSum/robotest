package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;

import java.util.List;
import java.util.UUID;

/**
 * 文档关联需求（脑图详设 4.3）：以 requirement → mindmap_document 的 derivation 边反向承载，
 * 读写均走追溯边，保证矩阵「需求 × 脑图文档」计数与文档侧关联同源。
 */
public interface DocumentRequirementService {

    /** 文档当前关联的需求列表（按需求编号升序），不含已断开的关联 */
    List<RequirementSummaryRespDTO> list(UUID projectId, UUID docId);

    /** 全量覆盖保存关联，返回保存后的完整列表；空集合表示清空 */
    List<RequirementSummaryRespDTO> save(UUID projectId, UUID docId, List<UUID> requirementIds, UUID userId);
}
