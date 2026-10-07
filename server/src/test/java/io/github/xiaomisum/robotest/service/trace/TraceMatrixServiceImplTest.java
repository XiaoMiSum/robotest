package io.github.xiaomisum.robotest.service.trace;

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
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceCoverageResult;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceCoverageResultMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 追溯矩阵服务单测：覆盖矩阵计数、链路遍历、边状态机、覆盖结论、缺口与影响处置（C8）。
 */
@ExtendWith(MockitoExtension.class)
class TraceMatrixServiceImplTest {

    /** 纯单测环境无 MyBatis 初始化：影响标记刷新用到 LambdaUpdateWrapperX 的列解析 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TraceEdge.class);
    }

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID REQ_ID = UUID.randomUUID();
    private static final UUID EDGE_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @Mock
    private TraceEdgeMapper traceEdgeMapper;
    @Mock
    private TraceCoverageResultMapper traceCoverageResultMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TraceNodeResolver nodeResolver;

    @InjectMocks
    private TraceMatrixServiceImpl service;

    // ---------- 3.2 矩阵 ----------

    @Test
    void matrix_countsEdgeTargetsAndFlags() {
        when(requirementMapper.findPage(any(), eq(PROJECT_ID), eq(List.of("confirmed")), isNull(), isNull(),
                isNull(), isNull(), isNull()))
                .thenReturn(new PageResult<>(List.of(requirement("confirmed")), 1L));
        when(traceCoverageResultMapper.listByRequirementIds(eq(PROJECT_ID), anyList()))
                .thenReturn(List.of(coverageRow("partial")));
        when(traceEdgeMapper.listActiveBySource(eq("requirement"), anyList()))
                .thenReturn(List.of(edge(REQ_ID, MODULE_ID, "module", "confirmed", null),
                        edge(REQ_ID, DOC_ID, "mindmap_document", "confirmed", null),
                        edge(REQ_ID, CASE_ID, "test_case", "confirmed", "v3")));
        when(traceEdgeMapper.listActiveBySource(eq("test_case"), anyList()))
                .thenReturn(List.of(edge(CASE_ID, REVIEW_ID, "test_review", "stale", null)));
        when(testCaseNodeMapper.listByIds(anyList())).thenReturn(List.of(caseNode(CASE_ID, 4)));

        TraceMatrixPageReqDTO req = new TraceMatrixPageReqDTO();
        req.setRequirementStatus("confirmed");
        TraceMatrixItemRespDTO row = service.matrix(req, PROJECT_ID).getList().get(0);

        assertEquals("partial", row.getCoverageStatus());
        TraceMatrixItemRespDTO.EdgeCounts counts = row.getEdgeCounts();
        assertEquals(1, counts.getModule());
        assertEquals(1, counts.getDocument());
        assertEquals(1, counts.getTestCase());
        assertEquals(1, counts.getReview());
        assertEquals(0, counts.getPlan());
        // stale 状态边 + 版本比对不一致的引用边都计入待重新确认
        assertEquals(2, row.getStaleCount());
        assertEquals(0, row.getConflictCount());
    }

    @Test
    void matrix_emptyPage_skipsEdgeQueries() {
        when(requirementMapper.findPage(any(), eq(PROJECT_ID), anyList(), isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(new PageResult<>(List.of(), 0L));

        assertTrue(service.matrix(new TraceMatrixPageReqDTO(), PROJECT_ID).getList().isEmpty());
        verify(traceEdgeMapper, never()).listActiveBySource(any(), anyList());
    }

    // ---------- 3.3 链路 ----------

    @Test
    void chain_down_returnsRootNodesAndMatchedEdges() {
        when(nodeResolver.requireNode(eq("requirement"), eq(REQ_ID), eq(PROJECT_ID)))
                .thenReturn(new TraceNodeResolver.NodeInfo("REQ-001 登录验证码", null));
        when(traceEdgeMapper.listActiveBySource(eq("requirement"), anyList()))
                .thenReturn(List.of(edge(REQ_ID, CASE_ID, "test_case", "confirmed", "v3")));
        when(traceEdgeMapper.listActiveBySource(eq("test_case"), anyList())).thenReturn(List.of());
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        TraceChainReqDTO req = new TraceChainReqDTO();
        req.setSourceType("requirement");
        req.setSourceId(REQ_ID);
        req.setDirection("sideways");
        TraceChainRespDTO resp = service.chain(req, PROJECT_ID);

        assertEquals("REQ-001 登录验证码", resp.getRoot().getTitle());
        assertEquals(1, resp.getNodes().size());
        assertEquals("v3", resp.getNodes().get(0).getVersion());
        assertEquals(1, resp.getEdges().size());
        assertTrue(resp.getEdges().get(0).getVersionMatched());
        assertFalse(resp.isHasMore());
    }

    @Test
    void chain_unsupportedSourceType_throwsNodeTypeUnsupported() {
        when(nodeResolver.requireNode(eq("bug"), eq(REQ_ID), eq(PROJECT_ID)))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_NODE_TYPE_UNSUPPORTED));

        TraceChainReqDTO req = new TraceChainReqDTO();
        req.setSourceType("bug");
        req.setSourceId(REQ_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.chain(req, PROJECT_ID));
        assertEquals(ErrorCodeConstants.TRACE_NODE_TYPE_UNSUPPORTED.code(), exception.getCode());
    }

    // ---------- 3.4 追溯边列表 ----------

    @Test
    void edges_unresolvableTarget_marksConflictForDisplay() {
        TraceEdge edge = edge(REQ_ID, CASE_ID, "test_case", "stale", "v3");
        when(traceEdgeMapper.findPage(any(), eq(PROJECT_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(new PageResult<>(List.of(edge), 1L));
        // 目标用例已删除：解析不到标题，按详设 3.4 展示层置 conflict
        when(nodeResolver.resolve(anyCollection())).thenReturn(new HashMap<>());

        TraceEdgeRespDTO resp = service.edges(new TraceEdgePageReqDTO(), PROJECT_ID).getList().get(0);

        assertEquals("conflict", resp.getStatus());
        assertNull(resp.getTarget().getTitle());
    }

    @Test
    void edges_resolvableNodes_keepPersistedStatus() {
        when(traceEdgeMapper.findPage(any(), eq(PROJECT_ID), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(new PageResult<>(List.of(edge(REQ_ID, CASE_ID, "test_case",
                        "stale", "v3")), 1L));
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        assertEquals("stale", service.edges(new TraceEdgePageReqDTO(), PROJECT_ID)
                .getList().get(0).getStatus());
    }

    // ---------- 3.5 新建边 ----------

    @Test
    void createEdge_success_writesManualConfirmedEdge() {
        when(nodeResolver.requireTitle(eq("requirement"), eq(REQ_ID), eq(PROJECT_ID)))
                .thenReturn("REQ-001 登录验证码");
        when(nodeResolver.requireNode(eq("test_case"), eq(CASE_ID), eq(PROJECT_ID)))
                .thenReturn(new TraceNodeResolver.NodeInfo("TC-001 获取验证码", "v4"));
        // 框架 insert 会回填主键：mock 需模拟该副作用，否则回读 selectById(null)
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, TraceEdge.class).setId(EDGE_ID);
            return 1;
        });
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge(REQ_ID, CASE_ID, "test_case",
                "confirmed", "v4"));
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        TraceEdgeCreateReqDTO req = new TraceEdgeCreateReqDTO();
        req.setEdgeType("derivation");
        req.setSourceType("requirement");
        req.setSourceId(REQ_ID);
        req.setTargetType("test_case");
        req.setTargetId(CASE_ID);

        TraceEdgeRespDTO resp = service.createEdge(req, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).insert(captor.capture());
        assertEquals("confirmed", captor.getValue().getStatus());
        assertEquals("manual", captor.getValue().getEstablishedBy());
        // 未显式传版本时按目标当前版本落 target_version
        assertEquals("v4", captor.getValue().getTargetVersion());
        assertEquals(OPERATOR_ID, captor.getValue().getConfirmedBy());
        assertEquals("derivation", resp.getEdgeType());
    }

    @Test
    void createEdge_duplicate_returnsDuplicateCode() {
        when(nodeResolver.requireTitle(eq("requirement"), eq(REQ_ID), eq(PROJECT_ID)))
                .thenReturn("REQ-001 登录验证码");
        when(nodeResolver.requireNode(eq("test_case"), eq(CASE_ID), eq(PROJECT_ID)))
                .thenReturn(new TraceNodeResolver.NodeInfo("TC-001", null));
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenThrow(new DuplicateKeyException("uk"));

        TraceEdgeCreateReqDTO req = new TraceEdgeCreateReqDTO();
        req.setEdgeType("derivation");
        req.setSourceType("requirement");
        req.setSourceId(REQ_ID);
        req.setTargetType("test_case");
        req.setTargetId(CASE_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createEdge(req, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_EDGE_DUPLICATE.code(), exception.getCode());
    }

    @Test
    void createEdge_illegalTypeCombo_throwsValidationFailed() {
        when(nodeResolver.requireTitle(eq("test_case"), eq(CASE_ID), eq(PROJECT_ID))).thenReturn("TC-001");
        when(nodeResolver.requireNode(eq("test_review"), eq(REVIEW_ID), eq(PROJECT_ID)))
                .thenReturn(new TraceNodeResolver.NodeInfo("评审 1", null));

        TraceEdgeCreateReqDTO req = new TraceEdgeCreateReqDTO();
        req.setEdgeType("derivation");
        req.setSourceType("test_case");
        req.setSourceId(CASE_ID);
        req.setTargetType("test_review");
        req.setTargetId(REVIEW_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createEdge(req, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void createEdge_unknownEdgeType_throwsValidationFailed() {
        TraceEdgeCreateReqDTO req = new TraceEdgeCreateReqDTO();
        req.setEdgeType("related");
        req.setSourceType("requirement");
        req.setSourceId(REQ_ID);
        req.setTargetType("test_case");
        req.setTargetId(CASE_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createEdge(req, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    // ---------- 3.6 边修正 ----------

    @Test
    void patchEdge_confirmOnDetached_throwsStateInvalid() {
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge(REQ_ID, CASE_ID, "test_case", "detached", null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchEdge(EDGE_ID, action("confirm"), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_EDGE_STATE_INVALID.code(), exception.getCode());
    }

    @Test
    void patchEdge_detachWithoutReason_throwsValidationFailed() {
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge(REQ_ID, CASE_ID, "test_case", "confirmed", null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchEdge(EDGE_ID, action("detach"), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
        verify(traceEdgeMapper, never()).updateById(any(TraceEdge.class));
    }

    @Test
    void patchEdge_detach_writesDetachedStatus() {
        // 首次读取为校验用 active 态，更新后回读为 detached 态
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(
                edge(REQ_ID, CASE_ID, "test_case", "confirmed", null),
                edge(REQ_ID, CASE_ID, "test_case", "detached", null));
        when(traceEdgeMapper.updateById(any(TraceEdge.class))).thenReturn(1);
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        TraceEdgePatchReqDTO req = action("detach");
        req.setReason("AI 误建");
        service.patchEdge(EDGE_ID, req, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).updateById(captor.capture());
        assertEquals("detached", captor.getValue().getStatus());
        assertEquals(OPERATOR_ID, captor.getValue().getConfirmedBy());
    }

    @Test
    void patchEdge_restoreOnActiveEdge_throwsStateInvalid() {
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge(REQ_ID, CASE_ID, "test_case", "confirmed", null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchEdge(EDGE_ID, action("restore"), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_EDGE_STATE_INVALID.code(), exception.getCode());
    }

    @Test
    void patchEdge_reattach_rewritesTargetAndVersion() {
        TraceEdge original = edge(REQ_ID, CASE_ID, "test_case", "confirmed", "v3");
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(original,
                edge(REQ_ID, MODULE_ID, "module", "confirmed", null));
        when(nodeResolver.requireNode(eq("module"), eq(MODULE_ID), eq(PROJECT_ID)))
                .thenReturn(new TraceNodeResolver.NodeInfo("登录模块", null));
        when(traceEdgeMapper.updateById(any(TraceEdge.class))).thenReturn(1);
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        TraceEdgePatchReqDTO req = action("reattach");
        req.setTargetType("module");
        req.setTargetId(MODULE_ID);
        service.patchEdge(EDGE_ID, req, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).updateById(captor.capture());
        assertEquals(MODULE_ID, captor.getValue().getTargetId());
        assertEquals("module", captor.getValue().getTargetType());
        assertEquals("confirmed", captor.getValue().getStatus());
    }

    @Test
    void patchEdge_unknownAction_throwsValidationFailed() {
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge(REQ_ID, CASE_ID, "test_case", "confirmed", null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchEdge(EDGE_ID, action("unlink"), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void patchEdge_crossProject_throwsEdgeNotFound() {
        TraceEdge foreign = edge(REQ_ID, CASE_ID, "test_case", "confirmed", null);
        foreign.setProjectId(UUID.randomUUID());
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(foreign);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchEdge(EDGE_ID, action("confirm"), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_EDGE_NOT_FOUND.code(), exception.getCode());
    }

    // ---------- 3.7 / 3.8 覆盖结论 ----------

    @Test
    void coverage_listMapsEvidence() {
        when(traceCoverageResultMapper.findPage(any(), eq(PROJECT_ID), anyList()))
                .thenReturn(new PageResult<>(List.of(coverageRow("uncovered")), 1L));

        TraceCoveragePageReqDTO req = new TraceCoveragePageReqDTO();
        req.setRequirementIds(REQ_ID.toString());
        TraceCoverageRespDTO resp = service.coverage(req, PROJECT_ID).getList().get(0);

        assertEquals(REQ_ID, resp.getRequirementId());
        assertEquals("uncovered", resp.getCoverageStatus());
    }

    @Test
    void coverage_invalidRequirementId_throwsValidationFailed() {
        TraceCoveragePageReqDTO req = new TraceCoveragePageReqDTO();
        req.setRequirementIds("not-a-uuid");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.coverage(req, PROJECT_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void patchCoverage_invalidStatus_throwsValidationFailed() {
        TraceCoveragePatchReqDTO req = new TraceCoveragePatchReqDTO();
        req.setCoverageStatus("unknown");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchCoverage(REQ_ID, req, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void patchCoverage_requirementMissing_throwsNodeNotFound() {
        when(requirementMapper.selectById(REQ_ID)).thenReturn(null);
        TraceCoveragePatchReqDTO req = new TraceCoveragePatchReqDTO();
        req.setCoverageStatus("covered");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchCoverage(REQ_ID, req, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_NODE_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void patchCoverage_withoutExistingRow_createsManualRow() {
        when(requirementMapper.selectById(REQ_ID)).thenReturn(requirement("confirmed"));
        when(traceCoverageResultMapper.findByRequirement(PROJECT_ID, REQ_ID)).thenReturn(null);
        when(traceCoverageResultMapper.insert(any(TraceCoverageResult.class))).thenReturn(1);

        TraceCoveragePatchReqDTO req = new TraceCoveragePatchReqDTO();
        req.setCoverageStatus("covered");
        req.setNote("人工判定");
        service.patchCoverage(REQ_ID, req, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<TraceCoverageResult> captor = ArgumentCaptor.forClass(TraceCoverageResult.class);
        verify(traceCoverageResultMapper).insert(captor.capture());
        assertEquals("covered", captor.getValue().getCoverageStatus());
        assertEquals(OPERATOR_ID, captor.getValue().getReviewedBy());
        assertNull(captor.getValue().getAiAnalyzedAt());
    }

    @Test
    void patchCoverage_withExistingRow_partialUpdatesReviewFields() {
        TraceCoverageResult existing = coverageRow("uncovered");
        existing.setReviewedBy(UUID.randomUUID());
        when(requirementMapper.selectById(REQ_ID)).thenReturn(requirement("confirmed"));
        when(traceCoverageResultMapper.findByRequirement(PROJECT_ID, REQ_ID)).thenReturn(existing);
        when(traceCoverageResultMapper.updateById(any(TraceCoverageResult.class))).thenReturn(1);
        TraceCoverageResult reloaded = coverageRow("partial");
        reloaded.setId(existing.getId());
        when(traceCoverageResultMapper.selectById(existing.getId())).thenReturn(reloaded);

        TraceCoveragePatchReqDTO req = new TraceCoveragePatchReqDTO();
        req.setCoverageStatus("partial");
        TraceCoverageRespDTO resp = service.patchCoverage(REQ_ID, req, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<TraceCoverageResult> captor = ArgumentCaptor.forClass(TraceCoverageResult.class);
        verify(traceCoverageResultMapper).updateById(captor.capture());
        // 部分更新（C11）：AI 分析字段不作为载体，保持原值不被覆盖
        assertNull(captor.getValue().getAnalyzedTaskId());
        assertNull(captor.getValue().getAiAnalyzedAt());
        assertEquals("partial", resp.getCoverageStatus());
    }

    // ---------- 4.2 AI 覆盖结论写入 ----------

    @Test
    void applyAiCoverage_invalidStatus_throwsValidationFailed() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.applyAiCoverage(PROJECT_ID, UUID.randomUUID(), REQ_ID, "pending",
                        Map.of()));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
        verify(traceCoverageResultMapper, never()).insert(any(TraceCoverageResult.class));
    }

    @Test
    void applyAiCoverage_withoutExistingRow_createsAiRow() {
        UUID taskId = UUID.randomUUID();
        when(traceCoverageResultMapper.findByRequirement(PROJECT_ID, REQ_ID)).thenReturn(null);
        when(traceCoverageResultMapper.insert(any(TraceCoverageResult.class))).thenReturn(1);

        boolean written = service.applyAiCoverage(PROJECT_ID, taskId, REQ_ID, "uncovered",
                Map.of("gaps", List.of("无关联用例")));

        assertTrue(written);
        ArgumentCaptor<TraceCoverageResult> captor = ArgumentCaptor.forClass(TraceCoverageResult.class);
        verify(traceCoverageResultMapper).insert(captor.capture());
        assertEquals("uncovered", captor.getValue().getCoverageStatus());
        assertEquals(taskId, captor.getValue().getAnalyzedTaskId());
        assertNotNull(captor.getValue().getAiAnalyzedAt());
        // reviewed_* 保持 NULL：AI 行结论可被人工修正（3.8）
        assertNull(captor.getValue().getReviewedBy());
    }

    @Test
    void applyAiCoverage_withExistingManualRow_skipsWrite() {
        TraceCoverageResult existing = coverageRow("covered");
        existing.setReviewedBy(UUID.randomUUID());
        when(traceCoverageResultMapper.findByRequirement(PROJECT_ID, REQ_ID)).thenReturn(existing);

        boolean written = service.applyAiCoverage(PROJECT_ID, UUID.randomUUID(), REQ_ID,
                "uncovered", Map.of("gaps", List.of("缺口")));

        assertFalse(written);
        verify(traceCoverageResultMapper, never()).insert(any(TraceCoverageResult.class));
        verify(traceCoverageResultMapper, never()).updateById(any(TraceCoverageResult.class));
    }

    @Test
    void applyAiCoverage_withExistingAiRow_partialUpdatesAiFields() {
        UUID taskId = UUID.randomUUID();
        TraceCoverageResult existing = coverageRow("uncovered");
        when(traceCoverageResultMapper.findByRequirement(PROJECT_ID, REQ_ID)).thenReturn(existing);
        when(traceCoverageResultMapper.updateById(any(TraceCoverageResult.class))).thenReturn(1);

        boolean written = service.applyAiCoverage(PROJECT_ID, taskId, REQ_ID, "partial",
                Map.of("coveredBy", List.of("用例A")));

        assertTrue(written);
        ArgumentCaptor<TraceCoverageResult> captor = ArgumentCaptor.forClass(TraceCoverageResult.class);
        verify(traceCoverageResultMapper).updateById(captor.capture());
        // 部分更新（C11）：载体只携带 id + AI 分析字段组，人工复核字段不触碰
        assertEquals(existing.getId(), captor.getValue().getId());
        assertEquals("partial", captor.getValue().getCoverageStatus());
        assertEquals(taskId, captor.getValue().getAnalyzedTaskId());
        assertNotNull(captor.getValue().getAiAnalyzedAt());
        assertNull(captor.getValue().getReviewedBy());
        assertNull(captor.getValue().getReviewedAt());
        assertNull(captor.getValue().getReviewedNote());
    }

    // ---------- 3.9 缺口 ----------

    @Test
    void gaps_uncoveredRequirement_givesGenerateAction() {
        when(traceEdgeMapper.countUncoveredRequirements(PROJECT_ID)).thenReturn(1L);
        when(traceEdgeMapper.pageUncoveredRequirements(eq(PROJECT_ID), eq(0L), anyInt()))
                .thenReturn(List.of(requirement("confirmed")));

        TraceGapPageReqDTO req = new TraceGapPageReqDTO();
        req.setType("uncovered_requirement");
        TraceGapRespDTO gap = service.gaps(req, PROJECT_ID).getList().get(0);

        assertEquals("requirement", gap.getTargetType());
        assertEquals("generate", gap.getSuggestedAction());
        assertEquals("uncovered", gap.getCoverageStatus());
    }

    @Test
    void gaps_orphanCase_givesReviewAction() {
        when(traceEdgeMapper.countOrphanCases(PROJECT_ID)).thenReturn(2L);
        when(traceEdgeMapper.pageOrphanCases(eq(PROJECT_ID), eq(0L), anyInt()))
                .thenReturn(List.of(caseNode(CASE_ID, 1)));

        TraceGapPageReqDTO req = new TraceGapPageReqDTO();
        req.setType("orphan_case");
        TraceGapRespDTO gap = service.gaps(req, PROJECT_ID).getList().get(0);

        assertEquals("test_case", gap.getTargetType());
        assertEquals("review", gap.getSuggestedAction());
        assertNull(gap.getCoverageStatus());
    }

    @Test
    void gaps_unscheduledCase_givesScheduleAction() {
        when(traceEdgeMapper.countUnscheduledCases(PROJECT_ID)).thenReturn(1L);
        when(traceEdgeMapper.pageUnscheduledCases(eq(PROJECT_ID), eq(0L), anyInt()))
                .thenReturn(List.of(caseNode(CASE_ID, 1)));

        TraceGapPageReqDTO req = new TraceGapPageReqDTO();
        req.setType("unscheduled_case");
        assertEquals("schedule", service.gaps(req, PROJECT_ID).getList().get(0).getSuggestedAction());
    }

    @Test
    void gaps_zeroCount_returnsEmptyWithoutPaging() {
        when(traceEdgeMapper.countUnreviewedCases(PROJECT_ID)).thenReturn(0L);

        TraceGapPageReqDTO req = new TraceGapPageReqDTO();
        req.setType("unreviewed_case");
        assertTrue(service.gaps(req, PROJECT_ID).getList().isEmpty());
        verify(traceEdgeMapper, never()).pageUnreviewedCases(any(), anyLong(), anyInt());
    }

    @Test
    void gaps_unknownType_throwsValidationFailed() {
        TraceGapPageReqDTO req = new TraceGapPageReqDTO();
        req.setType("broken_case");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.gaps(req, PROJECT_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    // ---------- 3.10 受影响项与处置 ----------

    @Test
    void impactItems_onlyListsMarkedEdges() {
        when(requirementMapper.selectById(REQ_ID)).thenReturn(requirement("changed"));
        TraceEdge marked = edge(REQ_ID, CASE_ID, "test_case", "confirmed", "v3");
        marked.setDisposition("pending");
        TraceEdge unmarked = edge(REQ_ID, MODULE_ID, "module", "confirmed", null);
        when(traceEdgeMapper.listActiveBySource(eq("requirement"), anyList()))
                .thenReturn(List.of(marked, unmarked));
        when(traceEdgeMapper.listActiveBySource(eq("test_case"), anyList())).thenReturn(List.of());
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        TraceImpactItemPageReqDTO req = new TraceImpactItemPageReqDTO();
        req.setRequirementId(REQ_ID);
        PageResult<TraceImpactItemRespDTO> page = service.impactItems(req, PROJECT_ID);

        assertEquals(1, page.getTotal());
        assertEquals("pending", page.getList().get(0).getDisposition());
        assertEquals("derivation", page.getList().get(0).getImpactType());
    }

    @Test
    void impactItems_requirementMissing_throwsImpactItemNotFound() {
        when(requirementMapper.selectById(REQ_ID)).thenReturn(null);

        TraceImpactItemPageReqDTO req = new TraceImpactItemPageReqDTO();
        req.setRequirementId(REQ_ID);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.impactItems(req, PROJECT_ID));
        assertEquals(ErrorCodeConstants.TRACE_IMPACT_ITEM_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void patchImpact_notMarked_throwsImpactItemNotFound() {
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge(REQ_ID, CASE_ID, "test_case", "confirmed", null));

        TraceImpactPatchReqDTO req = disposition("pending", null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchImpact(EDGE_ID, req, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_IMPACT_ITEM_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void patchImpact_alreadyDisposed_throwsDisposed() {
        TraceEdge edge = edge(REQ_ID, CASE_ID, "test_case", "confirmed", null);
        edge.setDisposition("regenerate");
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchImpact(EDGE_ID, disposition("no_impact", "不影响"), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.TRACE_IMPACT_ITEM_DISPOSED.code(), exception.getCode());
    }

    @Test
    void patchImpact_noImpactWithoutReason_throwsValidationFailed() {
        TraceEdge edge = edge(REQ_ID, CASE_ID, "test_case", "confirmed", null);
        edge.setDisposition("pending");
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.patchImpact(EDGE_ID, disposition("no_impact", null), PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
        verify(traceEdgeMapper, never()).updateById(any(TraceEdge.class));
    }

    @Test
    void patchImpact_noImpactWithReason_writesDisposition() {
        TraceEdge edge = edge(REQ_ID, CASE_ID, "test_case", "confirmed", null);
        edge.setDisposition("pending");
        TraceEdge disposed = edge(REQ_ID, CASE_ID, "test_case", "confirmed", null);
        disposed.setDisposition("no_impact");
        when(traceEdgeMapper.selectById(EDGE_ID)).thenReturn(edge, disposed);
        when(traceEdgeMapper.updateById(any(TraceEdge.class))).thenReturn(1);
        when(nodeResolver.resolve(anyCollection())).thenReturn(resolvedMap());

        TraceImpactItemRespDTO resp = service.patchImpact(EDGE_ID, disposition("no_impact", "描述变更无影响"),
                PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).updateById(captor.capture());
        assertEquals("no_impact", captor.getValue().getDisposition());
        assertEquals(OPERATOR_ID, captor.getValue().getDisposedBy());
        assertEquals("no_impact", resp.getDisposition());
    }

    // ---------- 4.3 影响标记刷新 ----------

    @Test
    void refreshImpactMarkers_resetsStaleDispositionsKeepsNoImpact() {
        when(requirementMapper.selectById(REQ_ID)).thenReturn(requirement("changed"));
        TraceEdge fresh = edge(REQ_ID, CASE_ID, "test_case", "confirmed", "v3");
        TraceEdge kept = edge(REQ_ID, MODULE_ID, "module", "confirmed", null);
        kept.setDisposition("no_impact");
        kept.setReason("无影响");
        TraceEdge stale = edge(REQ_ID, REVIEW_ID, "test_review", "confirmed", null);
        stale.setDisposition("re_review");
        when(traceEdgeMapper.listActiveBySource(eq("requirement"), anyList()))
                .thenReturn(List.of(fresh, kept, stale));
        when(traceEdgeMapper.listActiveBySource(eq("test_case"), anyList())).thenReturn(List.of());

        assertEquals(3, service.refreshImpactMarkers(PROJECT_ID, REQ_ID));
        // 新纳入项走部分更新写 pending；no_impact 保持不动；已失效处置回到 pending 并清空理由
        verify(traceEdgeMapper).updateById(any(TraceEdge.class));
        verify(traceEdgeMapper).update(isNull(), any());
    }

    @Test
    void refreshImpactMarkers_secondHopMarksCaseSnapshotEdges() {
        when(requirementMapper.selectById(REQ_ID)).thenReturn(requirement("changed"));
        TraceEdge derivation = edge(REQ_ID, CASE_ID, "test_case", "confirmed", "v3");
        TraceEdge snapshot = edge(CASE_ID, REVIEW_ID, "test_review", "confirmed", null);
        snapshot.setSourceType("test_case");
        when(traceEdgeMapper.listActiveBySource(eq("requirement"), anyList()))
                .thenReturn(List.of(derivation));
        when(traceEdgeMapper.listActiveBySource(eq("test_case"), anyList()))
                .thenReturn(List.of(snapshot));

        assertEquals(2, service.refreshImpactMarkers(PROJECT_ID, REQ_ID));
        verify(traceEdgeMapper, org.mockito.Mockito.times(2)).updateById(any(TraceEdge.class));
    }

    // ---------- 需求侧覆盖状态批量读取 ----------

    @Test
    void coverageStatuses_fallsBackToPendingForMissingRows() {
        when(traceCoverageResultMapper.listByRequirementIds(eq(PROJECT_ID), anyList()))
                .thenReturn(List.of(coverageRow("covered")));
        UUID other = UUID.randomUUID();

        Map<UUID, String> result = service.coverageStatuses(PROJECT_ID, List.of(REQ_ID, other));

        assertEquals("covered", result.get(REQ_ID));
        assertEquals("pending", result.get(other));
    }

    @Test
    void coverageStatuses_emptyInput_skipsQuery() {
        assertTrue(service.coverageStatuses(PROJECT_ID, List.of()).isEmpty());
        verify(traceCoverageResultMapper, never()).listByRequirementIds(any(), anyList());
    }

    // ---------- 构造辅助 ----------

    private Requirement requirement(String status) {
        Requirement item = new Requirement();
        item.setId(REQ_ID);
        item.setProjectId(PROJECT_ID);
        item.setCode("REQ-001");
        item.setTitle("登录验证码");
        item.setStatus(status);
        return item;
    }

    private TraceCoverageResult coverageRow(String status) {
        TraceCoverageResult row = new TraceCoverageResult();
        row.setId(UUID.randomUUID());
        row.setProjectId(PROJECT_ID);
        row.setRequirementId(REQ_ID);
        row.setCoverageStatus(status);
        return row;
    }

    private TraceEdge edge(UUID sourceId, UUID targetId, String targetType, String status,
            String targetVersion) {
        TraceEdge edge = new TraceEdge();
        edge.setId(UUID.randomUUID());
        edge.setProjectId(PROJECT_ID);
        edge.setEdgeType("test_review".equals(targetType) || "test_plan".equals(targetType)
                ? "case_snapshot" : "derivation");
        edge.setSourceType("requirement");
        edge.setSourceId(sourceId);
        edge.setTargetType(targetType);
        edge.setTargetId(targetId);
        edge.setTargetVersion(targetVersion);
        edge.setStatus(status);
        edge.setEstablishedBy("ai");
        edge.setCreatedAt(LocalDateTime.now());
        return edge;
    }

    private TestCaseNode caseNode(UUID id, int version) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setTitle("TC-001 获取验证码");
        node.setVersion(version);
        return node;
    }

    private Map<String, TraceNodeResolver.NodeInfo> resolvedMap() {
        Map<String, TraceNodeResolver.NodeInfo> map = new HashMap<>();
        map.put(TraceNodeResolver.key("requirement", REQ_ID),
                new TraceNodeResolver.NodeInfo("REQ-001 登录验证码", null));
        map.put(TraceNodeResolver.key("test_case", CASE_ID),
                new TraceNodeResolver.NodeInfo("TC-001 获取验证码", "v3"));
        map.put(TraceNodeResolver.key("module", MODULE_ID), new TraceNodeResolver.NodeInfo("登录模块", null));
        map.put(TraceNodeResolver.key("test_review", REVIEW_ID), new TraceNodeResolver.NodeInfo("评审 1", null));
        map.put(TraceNodeResolver.key("test_plan", PLAN_ID), new TraceNodeResolver.NodeInfo("计划 1", null));
        return map;
    }

    private TraceEdgePatchReqDTO action(String action) {
        TraceEdgePatchReqDTO req = new TraceEdgePatchReqDTO();
        req.setAction(action);
        return req;
    }

    private TraceImpactPatchReqDTO disposition(String disposition, String reason) {
        TraceImpactPatchReqDTO req = new TraceImpactPatchReqDTO();
        req.setDisposition(disposition);
        req.setReason(reason);
        return req;
    }
}
