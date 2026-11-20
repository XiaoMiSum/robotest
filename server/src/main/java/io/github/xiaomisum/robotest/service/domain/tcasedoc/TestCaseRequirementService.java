package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用例关联需求：需求摘要回显，并为计划/评审创建按需求反查关联用例。
 * 关联本身随脑图节点存于 ft_case_node.requirement_id，由 WS 持久化处理器写入，不设独立读写接口。
 */
public interface TestCaseRequirementService {

    /** 批量回显需求摘要（保序，缺失或跨项目跳过） */
    List<RequirementRefRespDTO> listRequirementRefs(UUID projectId, Collection<UUID> requirementIds);

    /**
     * 按需求解析关联用例，返回 documentId → caseIds；仅保留仍存在且 type=case 的节点。
     */
    Map<UUID, Set<UUID>> resolvePlannedCases(UUID projectId, Collection<UUID> requirementIds);
}
