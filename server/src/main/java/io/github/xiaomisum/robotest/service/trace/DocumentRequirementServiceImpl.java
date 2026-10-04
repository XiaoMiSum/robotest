package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.security.core.annotation.AuditLog;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档关联需求实现：查询按目标反查派生边（排除 detached，与矩阵 / 链路口径一致），
 * 保存经 {@link TraceEdgeWriter} 同事务对账，落库即有边。
 */
@Service
public class DocumentRequirementServiceImpl implements DocumentRequirementService {

    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TraceEdgeMapper traceEdgeMapper;
    @Resource
    private TraceEdgeWriter traceEdgeWriter;

    @Override
    public List<RequirementSummaryRespDTO> list(UUID projectId, UUID docId) {
        requireDocument(projectId, docId);
        Set<UUID> sourceIds = traceEdgeMapper
                .listDerivationsTo(projectId, Constants.TraceNodeType.MINDMAP_DOCUMENT, docId).stream()
                .filter(edge -> !Constants.TraceEdgeStatus.DETACHED.equals(edge.getStatus()))
                .map(TraceEdge::getSourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return toSummaries(sorted(requirementMapper.listByIds(sourceIds), projectId));
    }

    @Override
    @AuditLog(action = "SAVE:DocumentRequirements")
    @Transactional(rollbackFor = Exception.class)
    public List<RequirementSummaryRespDTO> save(UUID projectId, UUID docId, List<UUID> requirementIds, UUID userId) {
        requireDocument(projectId, docId);
        List<UUID> targetIds = requirementIds == null ? List.of() : requirementIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // 先整体校验再落边，避免半数成功写入
        Map<UUID, Requirement> requirements = requireRequirements(projectId, targetIds);
        traceEdgeWriter.syncDocumentRequirementEdges(projectId, docId, targetIds, userId);
        return toSummaries(targetIds.stream().map(requirements::get).sorted(codeAsc()).toList());
    }

    // ---------------------------------------------------------------- 私有

    private void requireDocument(UUID projectId, UUID docId) {
        TestCaseDocument document = docId == null ? null : testCaseDocumentMapper.selectById(docId);
        if (document == null || !Objects.equals(document.getProjectId(), projectId)) {
            // 越权与不存在统一「文档不存在」，不泄露存在性
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
    }

    private Map<UUID, Requirement> requireRequirements(UUID projectId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Requirement> found = requirementMapper.listByIds(ids).stream()
                .filter(item -> Objects.equals(item.getProjectId(), projectId))
                .collect(Collectors.toMap(Requirement::getId, item -> item, (a, b) -> a));
        for (UUID id : ids) {
            if (!found.containsKey(id)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
            }
        }
        return found;
    }

    private List<Requirement> sorted(List<Requirement> items, UUID projectId) {
        return items.stream()
                .filter(item -> Objects.equals(item.getProjectId(), projectId))
                .sorted(codeAsc())
                .toList();
    }

    private static Comparator<Requirement> codeAsc() {
        return Comparator.comparing(Requirement::getCode,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static List<RequirementSummaryRespDTO> toSummaries(List<Requirement> items) {
        return items.stream().map(item -> {
            RequirementSummaryRespDTO dto = new RequirementSummaryRespDTO();
            dto.setId(item.getId());
            dto.setCode(item.getCode());
            dto.setTitle(item.getTitle());
            return dto;
        }).toList();
    }
}
