package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TestCaseRequirementServiceImpl implements TestCaseRequirementService {

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private RequirementMapper requirementMapper;

    @Override
    public List<RequirementRefRespDTO> listRequirementRefs(UUID projectId, Collection<UUID> requirementIds) {
        List<UUID> targets = requirementIds == null ? List.of()
                : requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, RequirementRefRespDTO> refs = toRefs(projectId, targets).stream()
                .collect(Collectors.toMap(RequirementRefRespDTO::getId, Function.identity()));
        return targets.stream().map(refs::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Map<UUID, Set<UUID>> resolvePlannedCases(UUID projectId, Collection<UUID> requirementIds) {
        List<UUID> targets = requirementIds == null ? List.of()
                : requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return Map.of();
        }
        requireRequirements(projectId, targets);

        List<TestCaseNode> nodes = testCaseNodeMapper.listCasesByRequirementIds(targets);
        Map<UUID, Set<UUID>> byDocument = new LinkedHashMap<>();
        for (TestCaseNode node : nodes) {
            // 用例所属文档缺失时跳过，避免计划/评审出现孤立节点
            if (node.getDocumentId() == null) {
                continue;
            }
            byDocument.computeIfAbsent(node.getDocumentId(), key -> new LinkedHashSet<>()).add(node.getId());
        }
        return byDocument;
    }

    private void requireRequirements(UUID projectId, List<UUID> requirementIds) {
        if (requirementIds.isEmpty()) {
            return;
        }
        Map<UUID, Requirement> byId = requirementMapper.listByIds(requirementIds).stream()
                .collect(Collectors.toMap(Requirement::getId, Function.identity()));
        boolean valid = requirementIds.stream()
                .map(byId::get)
                .allMatch(requirement -> requirement != null
                        && Objects.equals(requirement.getProjectId(), projectId));
        if (!valid) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }
    }

    private List<RequirementRefRespDTO> toRefs(UUID projectId, List<UUID> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Requirement> byId = requirementMapper.listByIds(requirementIds).stream()
                .filter(requirement -> Objects.equals(requirement.getProjectId(), projectId))
                .collect(Collectors.toMap(Requirement::getId, Function.identity()));
        return requirementIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Requirement::getCode, TestCaseRequirementServiceImpl::compareCode))
                .map(requirement -> {
                    RequirementRefRespDTO dto = new RequirementRefRespDTO();
                    dto.setId(requirement.getId());
                    dto.setCode(requirement.getCode());
                    dto.setTitle(requirement.getTitle());
                    return dto;
                })
                .toList();
    }

    /** 需求编号按数字后缀排序（REQ-9 排在 REQ-10 前），非标准编号回退字典序 */
    private static int compareCode(String left, String right) {
        Integer leftSeq = numericSuffix(left);
        Integer rightSeq = numericSuffix(right);
        if (leftSeq != null && rightSeq != null) {
            return Integer.compare(leftSeq, rightSeq);
        }
        return Comparator.nullsLast(String::compareTo).compare(left, right);
    }

    private static Integer numericSuffix(String code) {
        if (code == null) {
            return null;
        }
        int index = code.lastIndexOf('-');
        if (index < 0 || index == code.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(code.substring(index + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
