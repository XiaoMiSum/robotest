package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseRequirement;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseRequirementMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.security.core.annotation.AuditLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
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
    private TestCaseRequirementMapper caseRequirementMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public List<RequirementRefRespDTO> listRequirements(UUID projectId, UUID caseId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        requireCaseNode(projectId, caseId, false);
        List<UUID> requirementIds = caseRequirementMapper.listByCaseNodeId(projectId, caseId).stream()
                .map(TestCaseRequirement::getRequirementId)
                .toList();
        return toRefs(projectId, requirementIds);
    }

    @Override
    @AuditLog(action = "SAVE:CaseRequirements")
    @Transactional(rollbackFor = Exception.class)
    public List<RequirementRefRespDTO> saveRequirements(UUID projectId, UUID caseId, List<UUID> requirementIds,
            UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        TestCaseNode node = requireCaseNode(projectId, caseId, true);
        List<UUID> targets = requirementIds == null ? List.of()
                : requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        requireRequirements(projectId, targets);

        List<TestCaseRequirement> existing = caseRequirementMapper.listByCaseNodeId(projectId, caseId);
        Set<UUID> existingIds = existing.stream()
                .map(TestCaseRequirement::getRequirementId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<UUID> targetIds = new HashSet<>(targets);

        existing.stream()
                .filter(row -> !targetIds.contains(row.getRequirementId()))
                .forEach(row -> caseRequirementMapper.deleteById(row.getId()));

        List<TestCaseRequirement> added = new ArrayList<>();
        for (UUID requirementId : targets) {
            if (existingIds.contains(requirementId)) {
                continue;
            }
            TestCaseRequirement row = new TestCaseRequirement();
            row.setProjectId(projectId);
            row.setRequirementId(requirementId);
            row.setCaseNodeId(caseId);
            row.setDocumentId(node.getDocumentId());
            added.add(row);
        }
        if (!added.isEmpty()) {
            caseRequirementMapper.insertBatch(added);
        }
        return toRefs(projectId, targets);
    }

    @Override
    public List<RequirementRefRespDTO> listRequirementRefs(UUID projectId, Collection<UUID> requirementIds) {
        List<UUID> targets = requirementIds == null ? List.of()
                : requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, RequirementRefRespDTO> refs = toRefs(projectId, targets).stream()
                .collect(Collectors.toMap(RequirementRefRespDTO::getId, Function.identity()));
        return targets.stream().map(refs::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Map<UUID, Set<UUID>> resolvePlannedCases(UUID projectId, Collection<UUID> requirementIds) {        List<UUID> targets = requirementIds == null ? List.of()
                : requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return Map.of();
        }
        requireRequirements(projectId, targets);

        List<TestCaseRequirement> rows = caseRequirementMapper.listByRequirementIds(projectId, targets);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TestCaseNode> nodes = testCaseNodeMapper
                .listByIds(rows.stream().map(TestCaseRequirement::getCaseNodeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TestCaseNode::getId, Function.identity()));

        Map<UUID, Set<UUID>> byDocument = new LinkedHashMap<>();
        for (TestCaseRequirement row : rows) {
            TestCaseNode node = nodes.get(row.getCaseNodeId());
            // 用例被删除或已改类型时不再带入，避免计划/评审出现非用例节点
            if (node == null || !Constants.NodeType.CASE.equals(node.getType()) || node.getDocumentId() == null) {
                continue;
            }
            byDocument.computeIfAbsent(node.getDocumentId(), key -> new LinkedHashSet<>()).add(node.getId());
        }
        return byDocument;
    }

    private TestCaseNode requireCaseNode(UUID projectId, UUID caseId, boolean requireCaseType) {
        TestCaseNode node = caseId == null ? null : testCaseNodeMapper.selectById(caseId);
        if (node == null || node.getDocumentId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(node.getDocumentId());
        if (document == null || !Objects.equals(document.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        if (requireCaseType && !Constants.NodeType.CASE.equals(node.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        return node;
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
