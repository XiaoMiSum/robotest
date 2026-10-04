package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
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
}
