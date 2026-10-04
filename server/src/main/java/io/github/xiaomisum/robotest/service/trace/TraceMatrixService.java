package io.github.xiaomisum.robotest.service.trace;

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
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 追溯矩阵服务（追溯矩阵详设 3.x / 4.x）：矩阵、链路、边维护、覆盖与影响处置的唯一实现方；
 * 需求管理经 {@link #chain} 与 {@link #coverageStatuses} 委托本服务（需求管理详设 3.12）。
 * 全部查询强制 project_id 隔离，越权按不存在处理（4.4）。
 */
public interface TraceMatrixService {

    /** 矩阵视图（3.2）：需求维度分页 + 边计数 + 覆盖状态联查 */
    PageResult<TraceMatrixItemRespDTO> matrix(TraceMatrixPageReqDTO req, UUID projectId);

    /** 链路视图（3.3）：从起点按方向遍历，深度上限 6、节点上限 2000 */
    TraceChainRespDTO chain(TraceChainReqDTO req, UUID projectId);

    /** 追溯边列表（3.4） */
    PageResult<TraceEdgeRespDTO> edges(TraceEdgePageReqDTO req, UUID projectId);

    /** 人工新建追溯边（3.5）：命中唯一约束回既有边并报 1000018153 */
    TraceEdgeRespDTO createEdge(TraceEdgeCreateReqDTO req, UUID projectId, UUID userId);

    /** 追溯边修正 confirm / reattach / detach / restore（3.6） */
    TraceEdgeRespDTO patchEdge(UUID edgeId, TraceEdgePatchReqDTO req, UUID projectId, UUID userId);

    /** 覆盖状态查询（3.7） */
    PageResult<TraceCoverageRespDTO> coverage(TraceCoveragePageReqDTO req, UUID projectId);

    /** 覆盖结论人工修正（3.8）：无结论则创建人工行，修正后 AI 分析不再覆盖该行 */
    TraceCoverageRespDTO patchCoverage(UUID requirementId, TraceCoveragePatchReqDTO req, UUID projectId,
            UUID userId);

    /** 缺口列表（3.9）：四类缺口与引导动作 */
    PageResult<TraceGapRespDTO> gaps(TraceGapPageReqDTO req, UUID projectId);

    /** 受影响项查询（3.10）：仅列出已纳入影响分析的边（disposition 非空） */
    PageResult<TraceImpactItemRespDTO> impactItems(TraceImpactItemPageReqDTO req, UUID projectId);

    /** 受影响项处置（3.10）：只改标记不改下游内容，已处置项返回 1000018159 */
    TraceImpactItemRespDTO patchImpact(UUID edgeId, TraceImpactPatchReqDTO req, UUID projectId, UUID userId);

    /**
     * 影响标记刷新（4.3）：从需求遍历 derivation 边与下游 case_snapshot 边逐项置 pending；
     * 已人工处置为 no_impact 的项按源 + 目标对匹配保留。返回本次标记的边数。
     */
    int refreshImpactMarkers(UUID projectId, UUID requirementId);

    /** 需求侧批量读取覆盖状态（无结论记录返回 pending；AI 开关语义由需求侧叠加） */
    Map<UUID, String> coverageStatuses(UUID projectId, Collection<UUID> requirementIds);
}
