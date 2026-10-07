package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceChainReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceCoveragePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceCoveragePatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceEdgeCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceEdgePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceEdgePatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceGapPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceImpactItemPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceImpactPatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceMatrixPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceChainRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceCoverageRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceEdgeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceGapRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceImpactItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceMatrixItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceNodeRefRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceCoverageResult;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceCoverageResultMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.security.core.annotation.AuditLog;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 追溯矩阵服务实现（追溯矩阵详设 4.1 / 4.4）：
 * 查询按 project_id 强制隔离，越权按不存在处理；detached 边不参与任何遍历与统计；
 * 派生边由 AI 建立、人工用例圈选由快照对账建立，AI 不自动重建 detached 边。
 */
@Service
public class TraceMatrixServiceImpl implements TraceMatrixService {

    /** 链路遍历上限（详设 3.3）：深度 6 层、节点 2000，任一触顶置 hasMore */
    private static final int MAX_DEPTH = 6;
    private static final int MAX_CHAIN_NODES = 2000;
    private static final int MAX_COVERAGE_IDS = 100;

    private static final Set<String> COVERABLE = Set.of(
            Constants.TraceCoverageStatus.COVERED,
            Constants.TraceCoverageStatus.PARTIAL,
            Constants.TraceCoverageStatus.UNCOVERED);
    private static final Set<String> DISPOSITIONS = Set.of(
            Constants.TraceDisposition.PENDING,
            Constants.TraceDisposition.REGENERATE,
            Constants.TraceDisposition.RE_REVIEW,
            Constants.TraceDisposition.NO_IMPACT);
    private static final Set<String> GAP_TYPES = Set.of(
            Constants.TraceGapType.UNCOVERED_REQUIREMENT,
            Constants.TraceGapType.ORPHAN_CASE,
            Constants.TraceGapType.UNREVIEWED_CASE,
            Constants.TraceGapType.UNSCHEDULED_CASE);
    private static final Set<String> DIRECTIONS = Set.of(
            Constants.TraceDirection.DOWN, Constants.TraceDirection.UP, Constants.TraceDirection.BOTH);

    /** 链路遍历中的节点坐标（type + id 构成节点标识） */
    private record Pt(String type, UUID id) {
    }

    /** stale / conflict 计数的可变累加器 */
    private static final class Flags {
        private int stale;
        private int conflict;
    }

    @Resource
    private TraceEdgeMapper traceEdgeMapper;
    @Resource
    private TraceCoverageResultMapper traceCoverageResultMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TraceNodeResolver nodeResolver;

    // ---------------------------------------------------------------- 3.2 矩阵视图

    @Override
    public PageResult<TraceMatrixItemRespDTO> matrix(TraceMatrixPageReqDTO req, UUID projectId) {
        List<String> statuses = req.getRequirementStatus() == null || req.getRequirementStatus().isBlank()
                ? List.of() : List.of(req.getRequirementStatus().trim());
        PageResult<Requirement> page = requirementMapper.findPage(req, projectId, statuses,
                null, null, null, req.getKeyword(), req.getCoverage());
        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }

        List<Requirement> items = page.getList();
        List<UUID> requirementIds = items.stream().map(Requirement::getId).toList();
        Map<UUID, String> coverageStatus = coverageStatuses(projectId, requirementIds);

        // 需求侧派生边（第一跳）与由其圈出的用例快照边（第二跳）
        List<TraceEdge> derivationEdges = traceEdgeMapper.listActiveBySource(
                Constants.TraceNodeType.REQUIREMENT, requirementIds);
        List<UUID> derivedCaseIds = derivationEdges.stream()
                .filter(edge -> Constants.TraceNodeType.TEST_CASE.equals(edge.getTargetType()))
                .map(TraceEdge::getTargetId).distinct().toList();
        List<TraceEdge> snapshotEdges = traceEdgeMapper.listActiveBySource(
                        Constants.TraceNodeType.TEST_CASE, derivedCaseIds).stream()
                .filter(edge -> Constants.TraceEdgeType.CASE_SNAPSHOT.equals(edge.getEdgeType()))
                .toList();
        Map<UUID, List<TraceEdge>> snapshotBySource = snapshotEdges.stream()
                .collect(Collectors.groupingBy(TraceEdge::getSourceId));

        // 版本比对（4.1）：矩阵侧用当前用例版本实时比对，stale 之外的不一致同样计入待确认
        List<UUID> caseTargets = new ArrayList<>(derivedCaseIds);
        snapshotEdges.stream().filter(edge -> Constants.TraceNodeType.TEST_CASE.equals(edge.getTargetType()))
                .map(TraceEdge::getTargetId).forEach(caseTargets::add);
        Map<UUID, String> liveCaseVersions = loadCaseVersions(caseTargets);

        List<TraceMatrixItemRespDTO> rows = new ArrayList<>();
        for (Requirement item : items) {
            TraceMatrixItemRespDTO row = new TraceMatrixItemRespDTO();
            row.setRequirementId(item.getId());
            row.setCode(item.getCode());
            row.setTitle(item.getTitle());
            row.setStatus(item.getStatus());
            row.setCoverageStatus(coverageStatus.getOrDefault(item.getId(),
                    Constants.TraceCoverageStatus.PENDING));

            TraceMatrixItemRespDTO.EdgeCounts counts = new TraceMatrixItemRespDTO.EdgeCounts();
            Flags flags = new Flags();
            List<UUID> casesOfRequirement = new ArrayList<>();
            derivationEdges.stream().filter(edge -> item.getId().equals(edge.getSourceId()))
                    .forEach(edge -> {
                        switch (edge.getTargetType()) {
                            case Constants.TraceNodeType.MODULE -> counts.setModule(counts.getModule() + 1);
                            case Constants.TraceNodeType.MINDMAP_DOCUMENT ->
                                counts.setDocument(counts.getDocument() + 1);
                            case Constants.TraceNodeType.TEST_CASE -> {
                                counts.setTestCase(counts.getTestCase() + 1);
                                casesOfRequirement.add(edge.getTargetId());
                            }
                            default -> {
                                // 其它目标类型不计入矩阵列
                            }
                        }
                        accumulate(edge, liveCaseVersions, flags);
                    });
            for (UUID caseId : casesOfRequirement) {
                for (TraceEdge edge : snapshotBySource.getOrDefault(caseId, List.of())) {
                    if (Constants.TraceNodeType.TEST_REVIEW.equals(edge.getTargetType())) {
                        counts.setReview(counts.getReview() + 1);
                    } else if (Constants.TraceNodeType.TEST_PLAN.equals(edge.getTargetType())) {
                        counts.setPlan(counts.getPlan() + 1);
                    }
                    accumulate(edge, liveCaseVersions, flags);
                }
            }
            row.setEdgeCounts(counts);
            row.setStaleCount(flags.stale);
            row.setConflictCount(flags.conflict);
            rows.add(row);
        }
        return new PageResult<>(rows, page.getTotal());
    }

    // ---------------------------------------------------------------- 3.3 链路视图

    @Override
    public TraceChainRespDTO chain(TraceChainReqDTO req, UUID projectId) {
        String rootType = req.getSourceType().trim();
        String direction = req.getDirection() == null || req.getDirection().isBlank()
                || !DIRECTIONS.contains(req.getDirection().trim())
                ? Constants.TraceDirection.DOWN : req.getDirection().trim();
        int depth = MAX_DEPTH;
        // 起点校验：节点不存在 / 越权 1000018152，类型不支持 1000018160
        nodeResolver.requireNode(rootType, req.getSourceId(), projectId);

        Map<Pt, Integer> levels = new LinkedHashMap<>();
        Pt root = new Pt(rootType, req.getSourceId());
        levels.put(root, 0);
        List<TraceEdge> traversed = new ArrayList<>();
        Set<UUID> seenEdges = new LinkedHashSet<>();
        boolean hasMore = false;

        List<Pt> frontier = new ArrayList<>(levels.keySet());
        boolean down = !Constants.TraceDirection.UP.equals(direction);
        boolean up = !Constants.TraceDirection.DOWN.equals(direction);
        outer:
        for (int level = 1; level <= depth && !frontier.isEmpty(); level++) {
            List<Pt> next = new ArrayList<>();
            Map<String, List<UUID>> frontierByType = frontier.stream().collect(
                    Collectors.groupingBy(Pt::type, Collectors.mapping(Pt::id, Collectors.toList())));
            for (Map.Entry<String, List<UUID>> entry : frontierByType.entrySet()) {
                List<TraceEdge> found = new ArrayList<>();
                if (down) {
                    found.addAll(traceEdgeMapper.listActiveBySource(entry.getKey(), entry.getValue()));
                }
                if (up) {
                    found.addAll(traceEdgeMapper.listActiveByTarget(entry.getKey(), entry.getValue()));
                }
                for (TraceEdge edge : found) {
                    boolean firstSeen = seenEdges.add(edge.getId());
                    if (firstSeen) {
                        traversed.add(edge);
                    }
                    Pt other = down && entry.getKey().equals(edge.getSourceType())
                            && entry.getValue().contains(edge.getSourceId())
                            ? new Pt(edge.getTargetType(), edge.getTargetId())
                            : new Pt(edge.getSourceType(), edge.getSourceId());
                    if (!levels.containsKey(other)) {
                        if (levels.size() >= MAX_CHAIN_NODES) {
                            hasMore = true;
                            break outer;
                        }
                        levels.put(other, level);
                        next.add(other);
                    }
                }
            }
            frontier = next;
        }
        // 深度耗尽仍有下层待展开
        hasMore = hasMore || !frontier.isEmpty();

        Set<TraceNodeRefRespDTO> refs = new LinkedHashSet<>();
        TraceNodeRefRespDTO rootRef = TraceNodeRefRespDTO.of(root.type(), root.id(), null);
        refs.add(rootRef);
        traversed.forEach(edge -> refs.addAll(TraceNodeResolver.refsOf(List.of(edge))));
        Map<String, TraceNodeResolver.NodeInfo> infos = nodeResolver.resolve(refs);

        TraceChainRespDTO resp = new TraceChainRespDTO();
        resp.setRoot(nodeRef(root.type(), root.id(),
                infos.get(TraceNodeResolver.key(root.type(), root.id()))));
        resp.setNodes(levels.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(root))
                .map(entry -> {
                    TraceChainRespDTO.ChainNode node = new TraceChainRespDTO.ChainNode();
                    node.setId(entry.getKey().id());
                    node.setType(entry.getKey().type());
                    TraceNodeResolver.NodeInfo info = infos.get(
                            TraceNodeResolver.key(entry.getKey().type(), entry.getKey().id()));
                    node.setTitle(info == null ? null : info.title());
                    node.setVersion(info == null ? null : info.version());
                    node.setLevel(entry.getValue());
                    return node;
                }).toList());
        resp.setEdges(traversed.stream().map(edge -> {
            TraceChainRespDTO.ChainEdge item = new TraceChainRespDTO.ChainEdge();
            item.setEdgeId(edge.getId());
            item.setSourceId(edge.getSourceId());
            item.setTargetId(edge.getTargetId());
            item.setEdgeType(edge.getEdgeType());
            item.setStatus(edge.getStatus());
            item.setTargetVersion(edge.getTargetVersion());
            item.setVersionMatched(versionMatched(edge, infos));
            return item;
        }).toList());
        resp.setHasMore(hasMore);
        return resp;
    }

    // ---------------------------------------------------------------- 3.4 追溯边列表

    @Override
    public PageResult<TraceEdgeRespDTO> edges(TraceEdgePageReqDTO req, UUID projectId) {
        PageResult<TraceEdge> page = traceEdgeMapper.findPage(req, projectId, req.getEdgeType(), req.getStatus(),
                req.getSourceType(), req.getSourceId(), req.getTargetType(), req.getTargetId());
        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        Map<String, TraceNodeResolver.NodeInfo> infos = nodeResolver.resolve(
                TraceNodeResolver.refsOf(page.getList()));
        List<TraceEdgeRespDTO> rows = page.getList().stream()
                .map(edge -> toEdgeResp(edge, infos, true))
                .toList();
        return new PageResult<>(rows, page.getTotal());
    }

    // ---------------------------------------------------------------- 3.5 新建边

    @Override
    @AuditLog(action = "CREATE:TraceEdge")
    @Transactional(rollbackFor = Exception.class)
    public TraceEdgeRespDTO createEdge(TraceEdgeCreateReqDTO req, UUID projectId, UUID userId) {
        String edgeType = normalizeEdgeType(req.getEdgeType());
        // 节点存在性与项目归属在类型组合校验前完成，越权与不存在统一 1000018152
        String sourceTitle = nodeResolver.requireTitle(req.getSourceType(), req.getSourceId(), projectId);
        TraceNodeResolver.NodeInfo target = nodeResolver.requireNode(
                req.getTargetType(), req.getTargetId(), projectId);
        validateCombo(edgeType, req.getSourceType(), req.getTargetType());

        TraceEdge edge = new TraceEdge();
        edge.setProjectId(projectId);
        edge.setEdgeType(edgeType);
        edge.setSourceType(req.getSourceType());
        edge.setSourceId(req.getSourceId());
        edge.setTargetType(req.getTargetType());
        edge.setTargetId(req.getTargetId());
        edge.setTargetVersion(req.getTargetVersion() != null && !req.getTargetVersion().isBlank()
                ? req.getTargetVersion() : target.version());
        edge.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
        edge.setEstablishedBy(Constants.TraceEstablishedBy.MANUAL);
        edge.setConfirmedBy(userId);
        edge.setConfirmedAt(LocalDateTime.now());
        try {
            traceEdgeMapper.insert(edge);
        } catch (DuplicateKeyException duplicate) {
            // 命中 uk_trace_edge_pair：既有行可能是有效边或 detached，引导改走 3.6 恢复 / 改挂
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_EDGE_DUPLICATE.code(),
                    "同一对节点已存在有效边（源 " + sourceTitle + "），请改用修正动作恢复或改挂");
        }
        return toEdgeResp(traceEdgeMapper.selectById(edge.getId()), resolveRefs(List.of(edge)), false);
    }

    // ---------------------------------------------------------------- 3.6 边修正

    @Override
    @AuditLog(action = "PATCH:TraceEdge")
    @Transactional(rollbackFor = Exception.class)
    public TraceEdgeRespDTO patchEdge(UUID edgeId, TraceEdgePatchReqDTO req, UUID projectId, UUID userId) {
        TraceEdge edge = requireEdge(edgeId, projectId);
        String action = req.getAction() == null ? "" : req.getAction().trim();
        TraceEdge carrier = new TraceEdge();
        carrier.setId(edgeId);
        carrier.setConfirmedBy(userId);
        carrier.setConfirmedAt(LocalDateTime.now());

        switch (action) {
            case Constants.TraceEdgeAction.CONFIRM -> {
                if (Constants.TraceEdgeStatus.DETACHED.equals(edge.getStatus())) {
                    // detached 的边只能先恢复（3.6 状态机），避免确认动作掩盖断开事实
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_EDGE_STATE_INVALID.code(),
                            "边已断开，请先执行恢复");
                }
                carrier.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
            }
            case Constants.TraceEdgeAction.REATTACH -> {
                if (req.getTargetType() == null || req.getTargetId() == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                            "改挂必须指定新的目标节点");
                }
                TraceNodeResolver.NodeInfo target = nodeResolver.requireNode(
                        req.getTargetType(), req.getTargetId(), projectId);
                validateCombo(edge.getEdgeType(), edge.getSourceType(), req.getTargetType());
                carrier.setTargetType(req.getTargetType());
                carrier.setTargetId(req.getTargetId());
                carrier.setTargetVersion(req.getTargetVersion() != null && !req.getTargetVersion().isBlank()
                        ? req.getTargetVersion() : target.version());
                carrier.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
            }
            case Constants.TraceEdgeAction.DETACH -> {
                if (req.getReason() == null || req.getReason().isBlank()) {
                    // 断开理由详设要求必填（5.2），随 @AuditLog 留痕，不落 trace_edge 列
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                            "断开必须填写理由");
                }
                carrier.setStatus(Constants.TraceEdgeStatus.DETACHED);
            }
            case Constants.TraceEdgeAction.RESTORE -> {
                if (!Constants.TraceEdgeStatus.DETACHED.equals(edge.getStatus())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_EDGE_STATE_INVALID.code(),
                            "仅已断开的边可以恢复");
                }
                carrier.setStatus(Constants.TraceEdgeStatus.CONFIRMED);
            }
            default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "修正动作取值非法：" + action);
        }

        try {
            traceEdgeMapper.updateById(carrier);
        } catch (DuplicateKeyException duplicate) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_EDGE_DUPLICATE.code(),
                    "目标节点上已存在有效边，请改用修正动作恢复或改挂");
        }
        TraceEdge updated = traceEdgeMapper.selectById(edgeId);
        return toEdgeResp(updated, resolveRefs(List.of(updated)), false);
    }

    // ---------------------------------------------------------------- 3.7 / 3.8 覆盖结论

    @Override
    public PageResult<TraceCoverageRespDTO> coverage(TraceCoveragePageReqDTO req, UUID projectId) {
        List<UUID> requirementIds = parseUuids(req.getRequirementIds());
        PageResult<TraceCoverageResult> page = traceCoverageResultMapper.findPage(req, projectId, requirementIds);
        if (page.getList().isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        return new PageResult<>(page.getList().stream().map(this::toCoverageResp).toList(), page.getTotal());
    }

    @Override
    @AuditLog(action = "PATCH:TraceCoverage")
    @Transactional(rollbackFor = Exception.class)
    public TraceCoverageRespDTO patchCoverage(UUID requirementId, TraceCoveragePatchReqDTO req, UUID projectId,
            UUID userId) {
        if (!COVERABLE.contains(req.getCoverageStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "覆盖状态取值非法：" + req.getCoverageStatus());
        }
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || !Objects.equals(requirement.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_NODE_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        TraceCoverageResult existing = traceCoverageResultMapper.findByRequirement(projectId, requirementId);
        if (existing == null) {
            TraceCoverageResult created = new TraceCoverageResult();
            created.setProjectId(projectId);
            created.setRequirementId(requirementId);
            created.setCoverageStatus(req.getCoverageStatus());
            created.setReviewedBy(userId);
            created.setReviewedNote(req.getNote());
            created.setReviewedAt(now);
            // ai_analyzed_at / analyzed_task_id / evidence 保持 NULL：本行结论来自人工修正
            traceCoverageResultMapper.insert(created);
            return toCoverageResp(created);
        }

        // 部分更新（C11）：只落本次修正涉及的字段，AI 分析字段保持历史值
        TraceCoverageResult carrier = new TraceCoverageResult();
        carrier.setId(existing.getId());
        carrier.setCoverageStatus(req.getCoverageStatus());
        carrier.setReviewedBy(userId);
        carrier.setReviewedNote(req.getNote());
        carrier.setReviewedAt(now);
        traceCoverageResultMapper.updateById(carrier);
        TraceCoverageResult updated = traceCoverageResultMapper.selectById(existing.getId());
        return toCoverageResp(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyAiCoverage(UUID projectId, UUID taskId, UUID requirementId, String coverageStatus,
            Map<String, Object> evidence) {
        if (!COVERABLE.contains(coverageStatus)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "覆盖状态取值非法：" + coverageStatus);
        }
        TraceCoverageResult existing = traceCoverageResultMapper.findByRequirement(projectId, requirementId);
        if (existing == null) {
            TraceCoverageResult created = new TraceCoverageResult();
            created.setProjectId(projectId);
            created.setRequirementId(requirementId);
            created.setCoverageStatus(coverageStatus);
            created.setEvidence(evidence);
            created.setAnalyzedTaskId(taskId);
            created.setAiAnalyzedAt(LocalDateTime.now());
            // reviewed_* 保持 NULL：本行结论来自 AI 分析，可被人工修正（3.8）
            traceCoverageResultMapper.insert(created);
            return true;
        }
        if (existing.getReviewedBy() != null) {
            // 人工判定优先（3.8）：修正后 AI 分析不再覆盖该行
            return false;
        }

        // 部分更新（C11）：只落本次 AI 分析字段组，人工复核字段不触碰
        TraceCoverageResult carrier = new TraceCoverageResult();
        carrier.setId(existing.getId());
        carrier.setCoverageStatus(coverageStatus);
        carrier.setEvidence(evidence);
        carrier.setAnalyzedTaskId(taskId);
        carrier.setAiAnalyzedAt(LocalDateTime.now());
        traceCoverageResultMapper.updateById(carrier);
        return true;
    }

    // ---------------------------------------------------------------- 3.9 缺口列表

    @Override
    public PageResult<TraceGapRespDTO> gaps(TraceGapPageReqDTO req, UUID projectId) {
        String type = req.getType().trim();
        if (!GAP_TYPES.contains(type)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "缺口类型取值非法：" + type);
        }
        long offset = (long) (req.getPageNo() - 1) * req.getPageSize();
        int limit = req.getPageSize();

        if (Constants.TraceGapType.UNCOVERED_REQUIREMENT.equals(type)) {
            long total = traceEdgeMapper.countUncoveredRequirements(projectId);
            if (total == 0) {
                return new PageResult<>(List.of(), 0L);
            }
            List<TraceGapRespDTO> rows = traceEdgeMapper.pageUncoveredRequirements(projectId, offset, limit)
                    .stream().map(item -> {
                        TraceGapRespDTO gap = new TraceGapRespDTO();
                        gap.setTargetType(Constants.TraceNodeType.REQUIREMENT);
                        gap.setTargetId(item.getId());
                        gap.setTitle(TraceNodeResolver.requirementTitle(item));
                        gap.setCoverageStatus(Constants.TraceCoverageStatus.UNCOVERED);
                        gap.setSuggestedAction("generate");
                        return gap;
                    }).toList();
            return new PageResult<>(rows, total);
        }

        // 三类用例缺口的取数与引导动作（3.9）
        final long total;
        final String action;
        if (Constants.TraceGapType.ORPHAN_CASE.equals(type)) {
            total = traceEdgeMapper.countOrphanCases(projectId);
            action = "review";
        } else if (Constants.TraceGapType.UNREVIEWED_CASE.equals(type)) {
            total = traceEdgeMapper.countUnreviewedCases(projectId);
            action = "review";
        } else {
            total = traceEdgeMapper.countUnscheduledCases(projectId);
            action = "schedule";
        }
        if (total == 0) {
            return new PageResult<>(List.of(), 0L);
        }
        List<TestCaseNode> cases;
        if (Constants.TraceGapType.ORPHAN_CASE.equals(type)) {
            cases = traceEdgeMapper.pageOrphanCases(projectId, offset, limit);
        } else if (Constants.TraceGapType.UNREVIEWED_CASE.equals(type)) {
            cases = traceEdgeMapper.pageUnreviewedCases(projectId, offset, limit);
        } else {
            cases = traceEdgeMapper.pageUnscheduledCases(projectId, offset, limit);
        }
        List<TraceGapRespDTO> rows = cases.stream().map(item -> {
            TraceGapRespDTO gap = new TraceGapRespDTO();
            gap.setTargetType(Constants.TraceNodeType.TEST_CASE);
            gap.setTargetId(item.getId());
            gap.setTitle(item.getTitle());
            gap.setCoverageStatus(null);
            gap.setSuggestedAction(action);
            return gap;
        }).toList();
        return new PageResult<>(rows, total);
    }

    // ---------------------------------------------------------------- 3.10 受影响项与处置

    @Override
    public PageResult<TraceImpactItemRespDTO> impactItems(TraceImpactItemPageReqDTO req, UUID projectId) {
        List<TraceEdge> marked = affectedEdges(projectId, req.getRequirementId()).stream()
                .filter(edge -> edge.getDisposition() != null)
                .filter(edge -> req.getDisposition() == null || req.getDisposition().isBlank()
                        || req.getDisposition().equals(edge.getDisposition()))
                .sorted(Comparator.comparing(TraceEdge::getCreatedAt).reversed())
                .toList();
        // 受影响项以需求为唯一入口，规模受单需求派生链约束，内存分页避免 UNION 复杂度
        int from = (int) Math.min((long) (req.getPageNo() - 1) * req.getPageSize(), marked.size());
        int to = Math.min(from + req.getPageSize(), marked.size());
        if (marked.isEmpty() || from >= marked.size()) {
            return new PageResult<>(List.of(), (long) marked.size());
        }
        Map<String, TraceNodeResolver.NodeInfo> infos = resolveRefs(marked);
        List<TraceImpactItemRespDTO> rows = marked.subList(from, to).stream().map(edge -> {
            TraceImpactItemRespDTO item = new TraceImpactItemRespDTO();
            item.setEdgeId(edge.getId());
            TraceNodeResolver.NodeInfo info = infos.get(
                    TraceNodeResolver.key(edge.getTargetType(), edge.getTargetId()));
            item.setTarget(nodeRef(edge.getTargetType(), edge.getTargetId(), info));
            item.setImpactType(edge.getEdgeType());
            item.setDisposition(edge.getDisposition());
            item.setReason(edge.getReason());
            item.setDisposedBy(edge.getDisposedBy());
            return item;
        }).toList();
        return new PageResult<>(rows, (long) marked.size());
    }

    @Override
    @AuditLog(action = "PATCH:TraceImpact")
    @Transactional(rollbackFor = Exception.class)
    public TraceImpactItemRespDTO patchImpact(UUID edgeId, TraceImpactPatchReqDTO req, UUID projectId,
            UUID userId) {
        TraceEdge edge = requireMarkedEdge(edgeId, projectId);
        String disposition = req.getDisposition().trim();
        if (!DISPOSITIONS.contains(disposition)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "处置标记取值非法：" + disposition);
        }
        if (!Constants.TraceDisposition.PENDING.equals(disposition)) {
            if (Constants.TraceDisposition.NO_IMPACT.equals(disposition)
                    && (req.getReason() == null || req.getReason().isBlank())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                        "确认无影响必须填写理由");
            }
            if (!Constants.TraceDisposition.PENDING.equals(edge.getDisposition())) {
                // 已处置项不可二次处置（3.10 状态机），需先由影响分析刷新回 pending
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_IMPACT_ITEM_DISPOSED);
            }
        }

        TraceEdge carrier = new TraceEdge();
        carrier.setId(edgeId);
        carrier.setDisposition(disposition);
        carrier.setReason(req.getReason());
        carrier.setDisposedBy(userId);
        traceEdgeMapper.updateById(carrier);

        TraceEdge updated = traceEdgeMapper.selectById(edgeId);
        TraceNodeResolver.NodeInfo info = nodeResolver.resolve(
                List.of(TraceNodeRefRespDTO.of(updated.getTargetType(), updated.getTargetId(), null)))
                .get(TraceNodeResolver.key(updated.getTargetType(), updated.getTargetId()));
        TraceImpactItemRespDTO resp = new TraceImpactItemRespDTO();
        resp.setEdgeId(updated.getId());
        resp.setTarget(nodeRef(updated.getTargetType(), updated.getTargetId(), info));
        resp.setImpactType(updated.getEdgeType());
        resp.setDisposition(updated.getDisposition());
        resp.setReason(updated.getReason());
        resp.setDisposedBy(updated.getDisposedBy());
        return resp;
    }

    // ---------------------------------------------------------------- 4.3 影响标记刷新

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshImpactMarkers(UUID projectId, UUID requirementId) {
        List<TraceEdge> edges = affectedEdges(projectId, requirementId);
        int marked = 0;
        for (TraceEdge edge : edges) {
            if (edge.getDisposition() == null) {
                TraceEdge carrier = new TraceEdge();
                carrier.setId(edge.getId());
                carrier.setDisposition(Constants.TraceDisposition.PENDING);
                traceEdgeMapper.updateById(carrier);
            } else if (!Constants.TraceDisposition.NO_IMPACT.equals(edge.getDisposition())
                    && !Constants.TraceDisposition.PENDING.equals(edge.getDisposition())) {
                // 其余处置结论在新一轮分析后失效：回到待处置并清空理由与处置人（4.3）
                traceEdgeMapper.update(null, new LambdaUpdateWrapperX<TraceEdge>()
                        .eq(TraceEdge::getId, edge.getId())
                        .set(TraceEdge::getDisposition, Constants.TraceDisposition.PENDING)
                        .set(TraceEdge::getReason, null)
                        .set(TraceEdge::getDisposedBy, null));
            }
            marked++;
        }
        return marked;
    }

    @Override
    public Map<UUID, String> coverageStatuses(UUID projectId, Collection<UUID> requirementIds) {
        Map<UUID, String> result = new LinkedHashMap<>();
        if (requirementIds == null || requirementIds.isEmpty()) {
            return result;
        }
        List<UUID> ids = requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        traceCoverageResultMapper.listByRequirementIds(projectId, ids)
                .forEach(row -> result.put(row.getRequirementId(), row.getCoverageStatus()));
        // 无结论记录即「待分析」（详设 2.4）：由查询侧推导，不落库
        ids.forEach(id -> result.putIfAbsent(id, Constants.TraceCoverageStatus.PENDING));
        return result;
    }

    // ---------------------------------------------------------------- 内部工具

    /** 需求 → 派生边 → 用例 → 快照边 的两跳遍历（4.3），返回受影响边集合（含未纳入标记的） */
    private List<TraceEdge> affectedEdges(UUID projectId, UUID requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || !Objects.equals(requirement.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_IMPACT_ITEM_NOT_FOUND);
        }
        List<TraceEdge> derivation = traceEdgeMapper.listActiveBySource(
                Constants.TraceNodeType.REQUIREMENT, List.of(requirementId));
        List<TraceEdge> result = new ArrayList<>(derivation);
        List<UUID> caseIds = derivation.stream()
                .filter(edge -> Constants.TraceNodeType.TEST_CASE.equals(edge.getTargetType()))
                .map(TraceEdge::getTargetId).distinct().toList();
        traceEdgeMapper.listActiveBySource(Constants.TraceNodeType.TEST_CASE, caseIds).stream()
                .filter(edge -> Constants.TraceEdgeType.CASE_SNAPSHOT.equals(edge.getEdgeType()))
                .forEach(result::add);
        return result;
    }

    private TraceEdge requireEdge(UUID edgeId, UUID projectId) {
        TraceEdge edge = traceEdgeMapper.selectById(edgeId);
        if (edge == null || !Objects.equals(edge.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_EDGE_NOT_FOUND);
        }
        return edge;
    }

    /** 受影响项必须已纳入影响分析（disposition 非空），否则按不存在处理（3.10） */
    private TraceEdge requireMarkedEdge(UUID edgeId, UUID projectId) {
        TraceEdge edge = requireEdge(edgeId, projectId);
        if (edge.getDisposition() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_IMPACT_ITEM_NOT_FOUND);
        }
        return edge;
    }

    private String normalizeEdgeType(String edgeType) {
        String value = edgeType == null ? "" : edgeType.trim();
        if (!Constants.TraceEdgeType.DERIVATION.equals(value)
                && !Constants.TraceEdgeType.CASE_SNAPSHOT.equals(value)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "边类型取值非法：" + edgeType);
        }
        return value;
    }

    /** 类型组合合法（3.5 / 2.4）：derivation 仅需求侧起点，case_snapshot 仅用例侧起点 */
    private void validateCombo(String edgeType, String sourceType, String targetType) {
        boolean valid;
        if (Constants.TraceEdgeType.DERIVATION.equals(edgeType)) {
            valid = switch (sourceType) {
                case Constants.TraceNodeType.REQUIREMENT, Constants.TraceNodeType.MODULE,
                     Constants.TraceNodeType.MINDMAP_DOCUMENT -> switch (targetType) {
                        case Constants.TraceNodeType.MODULE, Constants.TraceNodeType.MINDMAP_DOCUMENT,
                             Constants.TraceNodeType.TEST_CASE -> true;
                        default -> false;
                    };
                default -> false;
            };
        } else {
            valid = Constants.TraceNodeType.TEST_CASE.equals(sourceType)
                    && (Constants.TraceNodeType.TEST_REVIEW.equals(targetType)
                            || Constants.TraceNodeType.TEST_PLAN.equals(targetType));
        }
        if (!valid) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "边类型 " + edgeType + " 不允许 " + sourceType + " → " + targetType + " 的组合");
        }
    }

    private void accumulate(TraceEdge edge, Map<UUID, String> liveVersions, Flags flags) {
        if (Constants.TraceEdgeStatus.CONFLICT.equals(edge.getStatus())) {
            flags.conflict++;
        }
        if (Constants.TraceEdgeStatus.STALE.equals(edge.getStatus()) || versionStale(edge, liveVersions)) {
            flags.stale++;
        }
    }

    /** 实时版本比对：目标有版本概念且与引用时不一致即待重新确认（4.1 版本感知） */
    private boolean versionStale(TraceEdge edge, Map<UUID, String> liveVersions) {
        if (edge.getTargetVersion() == null || !Constants.TraceNodeType.TEST_CASE.equals(edge.getTargetType())) {
            return false;
        }
        String live = liveVersions.get(edge.getTargetId());
        return live != null && !live.equals(edge.getTargetVersion());
    }

    private Boolean versionMatched(TraceEdge edge, Map<String, TraceNodeResolver.NodeInfo> infos) {
        if (edge.getTargetVersion() == null) {
            return null;
        }
        TraceNodeResolver.NodeInfo info = infos.get(
                TraceNodeResolver.key(edge.getTargetType(), edge.getTargetId()));
        if (info == null) {
            return false;
        }
        return info.version() == null || info.version().equals(edge.getTargetVersion());
    }

    private Map<UUID, String> loadCaseVersions(Collection<UUID> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return Map.of();
        }
        return testCaseNodeMapper.listByIds(caseIds.stream().distinct().toList()).stream()
                .filter(node -> node.getVersion() != null)
                .collect(Collectors.toMap(TestCaseNode::getId,
                        node -> "v" + node.getVersion(), (a, b) -> a));
    }

    /** 组装节点引用并回填标题与版本（逻辑外键回填，详设 3.4） */
    private TraceNodeRefRespDTO nodeRef(String type, UUID id, TraceNodeResolver.NodeInfo info) {
        TraceNodeRefRespDTO ref = TraceNodeRefRespDTO.of(type, id, info == null ? null : info.title());
        ref.setVersion(info == null ? null : info.version());
        return ref;
    }

    private TraceEdgeRespDTO toEdgeResp(TraceEdge edge, Map<String, TraceNodeResolver.NodeInfo> infos,
            boolean markConflict) {
        TraceNodeResolver.NodeInfo source = infos.get(
                TraceNodeResolver.key(edge.getSourceType(), edge.getSourceId()));
        TraceNodeResolver.NodeInfo target = infos.get(
                TraceNodeResolver.key(edge.getTargetType(), edge.getTargetId()));
        TraceEdgeRespDTO resp = new TraceEdgeRespDTO();
        resp.setEdgeId(edge.getId());
        resp.setEdgeType(edge.getEdgeType());
        resp.setSource(nodeRef(edge.getSourceType(), edge.getSourceId(), source));
        resp.setTarget(nodeRef(edge.getTargetType(), edge.getTargetId(), target));
        resp.setTargetVersion(edge.getTargetVersion());
        // 解析不到的节点按 3.4 展示为 conflict（仅展示层，不回写状态列）
        boolean broken = source == null || target == null;
        resp.setStatus(markConflict && broken ? Constants.TraceEdgeStatus.CONFLICT : edge.getStatus());
        resp.setEstablishedBy(edge.getEstablishedBy());
        resp.setConfirmedBy(edge.getConfirmedBy());
        resp.setConfirmedAt(edge.getConfirmedAt());
        return resp;
    }

    private List<TraceNodeRefRespDTO> refsOfEdges(List<TraceEdge> edges) {
        Map<String, TraceNodeRefRespDTO> unique = new LinkedHashMap<>();
        edges.forEach(edge -> TraceNodeResolver.refsOf(List.of(edge)).forEach(ref ->
                unique.putIfAbsent(TraceNodeResolver.key(ref.getType(), ref.getId()), ref)));
        return List.copyOf(unique.values());
    }

    private Map<String, TraceNodeResolver.NodeInfo> resolveRefs(List<TraceEdge> edges) {
        return nodeResolver.resolve(refsOfEdges(edges));
    }

    private TraceCoverageRespDTO toCoverageResp(TraceCoverageResult row) {
        TraceCoverageRespDTO resp = new TraceCoverageRespDTO();
        resp.setRequirementId(row.getRequirementId());
        resp.setCoverageStatus(row.getCoverageStatus());
        resp.setEvidence(row.getEvidence());
        resp.setAiAnalyzedAt(row.getAiAnalyzedAt());
        resp.setReviewedBy(row.getReviewedBy());
        resp.setReviewedNote(row.getReviewedNote());
        resp.setReviewedAt(row.getReviewedAt());
        resp.setAnalyzedTaskId(row.getAnalyzedTaskId());
        return resp;
    }

    private List<UUID> parseUuids(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException invalid) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                        "需求 ID 格式非法：" + value);
            }
        }
        if (ids.size() > MAX_COVERAGE_IDS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED.code(),
                    "一次最多查询 " + MAX_COVERAGE_IDS + " 个需求 ID");
        }
        return ids;
    }
}
