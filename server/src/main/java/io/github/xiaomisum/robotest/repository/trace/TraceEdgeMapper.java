package io.github.xiaomisum.robotest.repository.trace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TraceEdgeMapper extends BaseMapperX<TraceEdge> {

    /** 追溯边列表（详设 3.4）：多条件筛选，隔离边界恒为当前项目 */
    default PageResult<TraceEdge> findPage(PageParam pageParam, UUID projectId, String edgeType, String status,
            String sourceType, UUID sourceId, String targetType, UUID targetId) {
        LambdaQueryWrapperX<TraceEdge> wrapper = new LambdaQueryWrapperX<TraceEdge>()
                .eq(TraceEdge::getProjectId, projectId)
                .eqIfPresent(TraceEdge::getEdgeType, edgeType)
                .eqIfPresent(TraceEdge::getStatus, status)
                .eqIfPresent(TraceEdge::getSourceType, sourceType)
                .eqIfPresent(TraceEdge::getSourceId, sourceId)
                .eqIfPresent(TraceEdge::getTargetType, targetType)
                .eqIfPresent(TraceEdge::getTargetId, targetId)
                .orderByDesc(TraceEdge::getCreatedAt);
        return selectPage(pageParam, wrapper);
    }

    /**
     * 某批源节点的有效出边：排除 detached（4.4 遍历不计断开的边），供链路正向遍历与矩阵计数。
     * 逻辑删除由 @TableLogic 自动附加，原生 SQL 才需显式过滤。
     */
    default List<TraceEdge> listActiveBySource(String sourceType, Collection<UUID> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TraceEdge>()
                .eq(TraceEdge::getSourceType, sourceType)
                .in(TraceEdge::getSourceId, sourceIds)
                .ne(TraceEdge::getStatus, Constants.TraceEdgeStatus.DETACHED));
    }

    /** 某批目标节点的有效入边（链路反向回溯、缺口与影响分析共用） */
    default List<TraceEdge> listActiveByTarget(String targetType, Collection<UUID> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TraceEdge>()
                .eq(TraceEdge::getTargetType, targetType)
                .in(TraceEdge::getTargetId, targetIds)
                .ne(TraceEdge::getStatus, Constants.TraceEdgeStatus.DETACHED));
    }

    /**
     * 目标节点下的派生入边（文档关联需求对账，详设 4.1）：
     * 含 detached——唯一索引 uk_trace_edge_pair 对未删除行始终生效，对账需据此恢复而非重复插入。
     */
    default List<TraceEdge> listDerivationsTo(UUID projectId, String targetType, UUID targetId) {
        return selectList(new LambdaQueryWrapperX<TraceEdge>()
                .eq(TraceEdge::getProjectId, projectId)
                .eq(TraceEdge::getEdgeType, Constants.TraceEdgeType.DERIVATION)
                .eq(TraceEdge::getTargetType, targetType)
                .eq(TraceEdge::getTargetId, targetId));
    }

    /** 目标节点下的快照引用边（快照圈选对账，详设 4.1） */
    default List<TraceEdge> listCaseSnapshots(UUID projectId, String targetType, UUID targetId) {
        return selectList(new LambdaQueryWrapperX<TraceEdge>()
                .eq(TraceEdge::getProjectId, projectId)
                .eq(TraceEdge::getEdgeType, Constants.TraceEdgeType.CASE_SNAPSHOT)
                .eq(TraceEdge::getTargetType, targetType)
                .eq(TraceEdge::getTargetId, targetId));
    }

    // ---------- 缺口列表（详设 3.9）：原生 SQL 承载 NOT EXISTS 关联，显式过滤逻辑删除与 detached ----------

    String UNCOVERED_REQUIREMENT_FROM = " FROM requirement r "
            + "JOIN trace_coverage_result c ON c.requirement_id = r.id AND c.project_id = r.project_id "
            + "AND c.is_deleted = FALSE "
            + "WHERE r.project_id = #{projectId} AND r.is_deleted = FALSE AND r.status <> 'archived' "
            + "AND c.coverage_status = 'uncovered' ";

    String CASE_FROM_WITH_DERIVATION = " FROM test_case_node n "
            + "JOIN test_case_document d ON d.id = n.document_id AND d.is_deleted = FALSE "
            + "WHERE d.project_id = #{projectId} AND n.is_deleted = FALSE AND n.type = 'case' "
            + "AND EXISTS (SELECT 1 FROM trace_edge e WHERE e.is_deleted = FALSE "
            + "AND e.edge_type = 'derivation' AND e.target_type = 'test_case' AND e.target_id = n.id "
            + "AND e.status <> 'detached' AND EXISTS (SELECT 1 FROM requirement r WHERE r.id = e.source_id "
            + "AND r.is_deleted = FALSE AND r.status <> 'archived')) ";

    String CASE_COLUMNS = "SELECT n.id, n.title, n.version, n.document_id ";

    /** 未覆盖需求（排除已归档） */
    @Select("SELECT r.id, r.code, r.title, r.status" + UNCOVERED_REQUIREMENT_FROM
            + "ORDER BY r.updated_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Requirement> pageUncoveredRequirements(@Param("projectId") UUID projectId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*)" + UNCOVERED_REQUIREMENT_FROM)
    long countUncoveredRequirements(@Param("projectId") UUID projectId);

    /** 孤儿用例：无有效派生入边 */
    @Select(CASE_COLUMNS + "FROM test_case_node n "
            + "JOIN test_case_document d ON d.id = n.document_id AND d.is_deleted = FALSE "
            + "WHERE d.project_id = #{projectId} AND n.is_deleted = FALSE AND n.type = 'case' "
            + "AND NOT EXISTS (SELECT 1 FROM trace_edge e WHERE e.is_deleted = FALSE "
            + "AND e.edge_type = 'derivation' AND e.target_type = 'test_case' AND e.target_id = n.id "
            + "AND e.status <> 'detached') "
            + "ORDER BY n.created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<TestCaseNode> pageOrphanCases(@Param("projectId") UUID projectId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM test_case_node n "
            + "JOIN test_case_document d ON d.id = n.document_id AND d.is_deleted = FALSE "
            + "WHERE d.project_id = #{projectId} AND n.is_deleted = FALSE AND n.type = 'case' "
            + "AND NOT EXISTS (SELECT 1 FROM trace_edge e WHERE e.is_deleted = FALSE "
            + "AND e.edge_type = 'derivation' AND e.target_type = 'test_case' AND e.target_id = n.id "
            + "AND e.status <> 'detached')")
    long countOrphanCases(@Param("projectId") UUID projectId);

    /** 未评审用例：有有效派生入边但无评审快照引用边 */
    @Select(CASE_COLUMNS + CASE_FROM_WITH_DERIVATION
            + "AND NOT EXISTS (SELECT 1 FROM trace_edge s WHERE s.is_deleted = FALSE "
            + "AND s.edge_type = 'case_snapshot' AND s.source_type = 'test_case' AND s.source_id = n.id "
            + "AND s.target_type = 'test_review' AND s.status <> 'detached') "
            + "ORDER BY n.created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<TestCaseNode> pageUnreviewedCases(@Param("projectId") UUID projectId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*)" + CASE_FROM_WITH_DERIVATION
            + "AND NOT EXISTS (SELECT 1 FROM trace_edge s WHERE s.is_deleted = FALSE "
            + "AND s.edge_type = 'case_snapshot' AND s.source_type = 'test_case' AND s.source_id = n.id "
            + "AND s.target_type = 'test_review' AND s.status <> 'detached')")
    long countUnreviewedCases(@Param("projectId") UUID projectId);

    /** 未入计划用例：有有效派生入边但无计划快照引用边 */
    @Select(CASE_COLUMNS + CASE_FROM_WITH_DERIVATION
            + "AND NOT EXISTS (SELECT 1 FROM trace_edge s WHERE s.is_deleted = FALSE "
            + "AND s.edge_type = 'case_snapshot' AND s.source_type = 'test_case' AND s.source_id = n.id "
            + "AND s.target_type = 'test_plan' AND s.status <> 'detached') "
            + "ORDER BY n.created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<TestCaseNode> pageUnscheduledCases(@Param("projectId") UUID projectId,
            @Param("offset") long offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*)" + CASE_FROM_WITH_DERIVATION
            + "AND NOT EXISTS (SELECT 1 FROM trace_edge s WHERE s.is_deleted = FALSE "
            + "AND s.edge_type = 'case_snapshot' AND s.source_type = 'test_case' AND s.source_id = n.id "
            + "AND s.target_type = 'test_plan' AND s.status <> 'detached')")
    long countUnscheduledCases(@Param("projectId") UUID projectId);
}
