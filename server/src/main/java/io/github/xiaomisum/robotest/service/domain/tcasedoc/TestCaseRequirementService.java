package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用例节点关联需求：查询/保存当前用例的需求关联，并为计划/评审创建按需求反查关联用例。
 */
public interface TestCaseRequirementService {

    List<RequirementRefRespDTO> listRequirements(UUID projectId, UUID caseId, UUID userId);

    List<RequirementRefRespDTO> saveRequirements(UUID projectId, UUID caseId, List<UUID> requirementIds,
            UUID userId);

    /** 批量回显需求摘要（保序，缺失或跨项目跳过） */
    List<RequirementRefRespDTO> listRequirementRefs(UUID projectId, Collection<UUID> requirementIds);

    /**
     * 按需求解析关联用例，返回 documentId → caseIds；仅保留仍存在且 type=case 的节点。
     */
    Map<UUID, Set<UUID>> resolvePlannedCases(UUID projectId, Collection<UUID> requirementIds);
}
