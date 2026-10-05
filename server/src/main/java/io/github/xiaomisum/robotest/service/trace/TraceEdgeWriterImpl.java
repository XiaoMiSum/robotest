package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 追溯边写入口实现（追溯矩阵详设 4.1）：
 * 同事务对账圈选集合——新增边固定 status = confirmed / established_by = manual（2.2 人工圈选口径），
 * 已移出集合的边逻辑删除；重复插入由并发事务先建时静默跳过，交由唯一约束裁决。
 */
@Service
public class TraceEdgeWriterImpl implements TraceEdgeWriter {

    @Resource
    private TraceEdgeMapper traceEdgeMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private RequirementMapper requirementMapper;

    @Override
    public void syncCaseSnapshotEdges(UUID projectId, String targetType, UUID targetId,
            Collection<UUID> caseIds, UUID operatorId) {
        Set<UUID> selected = caseIds == null ? Set.of() : caseIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<TraceEdge> existing = traceEdgeMapper.listCaseSnapshots(projectId, targetType, targetId);
        Map<UUID, TraceEdge> existingBySource = existing.stream()
                .collect(Collectors.toMap(TraceEdge::getSourceId, edge -> edge, (a, b) -> a));

        for (TraceEdge edge : existing) {
            if (!selected.contains(edge.getSourceId())) {
                traceEdgeMapper.deleteById(edge.getId());
            }
        }
        if (selected.isEmpty()) {
            return;
        }

        List<TestCaseNode> nodes = testCaseNodeMapper.listByIds(selected);
        for (TestCaseNode node : nodes) {
            if (existingBySource.containsKey(node.getId())) {
                continue;
            }
            TraceEdge edge = new TraceEdge();
            edge.setProjectId(projectId);
            edge.setEdgeType(Constants.TraceEdgeType.CASE_SNAPSHOT);
            edge.setSourceType(Constants.TraceNodeType.TEST_CASE);
            edge.setSourceId(node.getId());
            edge.setTargetType(targetType);
            edge.setTargetId(targetId);
            edge.setTargetVersion(TraceNodeResolver.caseVersion(node));
            edge.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
            edge.setEstablishedBy(Constants.TraceEstablishedBy.MANUAL);
            edge.setConfirmedBy(operatorId);
            edge.setConfirmedAt(LocalDateTime.now());
            try {
                traceEdgeMapper.insert(edge);
            } catch (DuplicateKeyException concurrent) {
                // 并发圈选同一目标时对账重复建边：以唯一约束先到者为准，不中断快照事务
            }
        }
    }

    @Override
    public void removeCaseSnapshotEdges(UUID projectId, String targetType, UUID targetId) {
        traceEdgeMapper.listCaseSnapshots(projectId, targetType, targetId)
                .forEach(edge -> traceEdgeMapper.deleteById(edge.getId()));
    }

    @Override
    public void syncDocumentRequirementEdges(UUID projectId, UUID docId, Collection<UUID> requirementIds,
            UUID operatorId) {
        Set<UUID> selected = requirementIds == null ? Set.of() : requirementIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<TraceEdge> existing = traceEdgeMapper.listDerivationsTo(
                projectId, Constants.TraceNodeType.MINDMAP_DOCUMENT, docId);
        Map<UUID, TraceEdge> existingBySource = existing.stream()
                .collect(Collectors.toMap(TraceEdge::getSourceId, edge -> edge, (a, b) -> a));

        for (TraceEdge edge : existing) {
            if (!selected.contains(edge.getSourceId())) {
                traceEdgeMapper.deleteById(edge.getId());
            }
        }
        if (selected.isEmpty()) {
            return;
        }

        for (Requirement requirement : requirementMapper.listByIds(selected)) {
            TraceEdge edge = existingBySource.get(requirement.getId());
            if (edge != null) {
                if (Constants.TraceEdgeStatus.DETACHED.equals(edge.getStatus())) {
                    // 重新勾选即人工恢复（2.2 断开留痕由审计承载），处置标记与既有状态保持不变
                    TraceEdge carrier = new TraceEdge();
                    carrier.setId(edge.getId());
                    carrier.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
                    carrier.setEstablishedBy(Constants.TraceEstablishedBy.MANUAL);
                    carrier.setConfirmedBy(operatorId);
                    carrier.setConfirmedAt(LocalDateTime.now());
                    traceEdgeMapper.updateById(carrier);
                }
                continue;
            }
            TraceEdge created = new TraceEdge();
            created.setProjectId(projectId);
            created.setEdgeType(Constants.TraceEdgeType.DERIVATION);
            created.setSourceType(Constants.TraceNodeType.REQUIREMENT);
            created.setSourceId(requirement.getId());
            created.setTargetType(Constants.TraceNodeType.MINDMAP_DOCUMENT);
            created.setTargetId(docId);
            created.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
            created.setEstablishedBy(Constants.TraceEstablishedBy.MANUAL);
            created.setConfirmedBy(operatorId);
            created.setConfirmedAt(LocalDateTime.now());
            try {
                traceEdgeMapper.insert(created);
            } catch (DuplicateKeyException concurrent) {
                // 并发保存同一文档时对账重复建边：以唯一约束先到者为准，不中断保存事务
            }
        }
    }

    @Override
    public void writeAiDerivationEdges(UUID projectId, Collection<UUID> requirementIds, String targetType,
            UUID targetId, String targetVersion) {
        Set<UUID> sources = requirementIds == null ? Set.of() : requirementIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sources.isEmpty()) {
            return;
        }

        // 同对已有边（含 detached）一律不重建：detached 不得由 AI 复活（详设 2.4）
        Set<UUID> existingSources = traceEdgeMapper.listDerivationsTo(projectId, targetType, targetId).stream()
                .map(TraceEdge::getSourceId)
                .collect(Collectors.toSet());

        for (UUID sourceId : sources) {
            if (existingSources.contains(sourceId)) {
                continue;
            }
            TraceEdge edge = new TraceEdge();
            edge.setProjectId(projectId);
            edge.setEdgeType(Constants.TraceEdgeType.DERIVATION);
            edge.setSourceType(Constants.TraceNodeType.REQUIREMENT);
            edge.setSourceId(sourceId);
            edge.setTargetType(targetType);
            edge.setTargetId(targetId);
            edge.setTargetVersion(targetVersion);
            edge.setStatus(Constants.TraceEdgeStatus.AI_CREATED);
            edge.setEstablishedBy(Constants.TraceEstablishedBy.AI);
            try {
                traceEdgeMapper.insert(edge);
            } catch (DuplicateKeyException concurrent) {
                // 采纳事务并发建同对边：以唯一约束先到者为准，不中断落库事务
            }
        }
    }
}
