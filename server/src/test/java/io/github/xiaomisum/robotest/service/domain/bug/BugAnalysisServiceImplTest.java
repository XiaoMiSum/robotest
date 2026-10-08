package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugAnalysisServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String FROM = "2026-10-01";
    private static final String TO = "2026-10-03";
    private static final LocalDateTime FROM_START = LocalDateTime.parse("2026-10-01T00:00:00");
    private static final LocalDateTime TO_END = LocalDateTime.parse("2026-10-04T00:00:00");

    @Mock
    private BugMapper bugMapper;
    @Mock
    private BugLogMapper bugLogMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private VectorSearchService vectorSearchService;

    @InjectMocks
    private BugAnalysisServiceImpl service;

    private static BugAnalysisQueryReqDTO query(String from, String to, String groupBy) {
        BugAnalysisQueryReqDTO query = new BugAnalysisQueryReqDTO();
        query.setFrom(from);
        query.setTo(to);
        query.setGroupBy(groupBy);
        return query;
    }

    private static Bug bug(String status, LocalDateTime createdAt, LocalDateTime closedAt) {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(PROJECT_ID);
        bug.setStatus(status);
        bug.setCreatedAt(createdAt);
        bug.setClosedAt(closedAt);
        return bug;
    }

    // ---------- 3.2 趋势 ----------

    @Test
    void trends_axisByDayAndSingleSeries() {
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any()))
                .thenReturn(List.of(bug("closed", LocalDateTime.parse("2026-10-01T10:00:00"),
                        LocalDateTime.parse("2026-10-02T10:00:00"))));

        BugTrendsRespDTO resp = service.trends(query(FROM, TO, "none"), PROJECT_ID, USER_ID);

        assertEquals(List.of("2026-10-01", "2026-10-02", "2026-10-03"), resp.getAxis());
        assertEquals("none", resp.getGroupBy());
        assertEquals(1, resp.getSeries().size());
        BugTrendsRespDTO.Series series = resp.getSeries().get(0);
        assertEquals("all", series.getKey());
        assertEquals("全部", series.getLabel());
        // 10-01 新增 1；10-02 关闭 1 且当日 24:00 已不活跃；10-03 起存量归 0
        assertEquals(Integer.valueOf(0), series.getClosed().get(0));
        assertEquals(Integer.valueOf(1), series.getClosed().get(1));
        assertEquals(Integer.valueOf(1), series.getActive().get(0));
        assertEquals(Integer.valueOf(0), series.getActive().get(1));
        assertEquals(Integer.valueOf(0), series.getActive().get(2));
    }

    @Test
    void trends_groupByModuleUsesModuleNameLabel() {
        ProjectModule module = new ProjectModule();
        module.setId(UUID.randomUUID());
        module.setName("登录模块");
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module));
        Bug grouped = bug("active", LocalDateTime.parse("2026-10-01T10:00:00"), null);
        grouped.setModuleId(module.getId());
        Bug ungrouped = bug("active", LocalDateTime.parse("2026-10-01T11:00:00"), null);
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of(grouped, ungrouped));

        BugTrendsRespDTO resp = service.trends(query(FROM, TO, "module"), PROJECT_ID, USER_ID);

        assertEquals("module", resp.getGroupBy());
        assertEquals(2, resp.getSeries().size());
        // 组间排序对条数相同的键不稳定，按 label 断言而非下标
        Map<String, BugTrendsRespDTO.Series> byLabel = resp.getSeries().stream()
                .collect(Collectors.toMap(BugTrendsRespDTO.Series::getLabel, series -> series));
        assertEquals(Integer.valueOf(1), byLabel.get("登录模块").getActive().get(0));
        assertEquals(Integer.valueOf(1), byLabel.get("未指定模块").getActive().get(0));
    }

    @Test
    void trends_invalidGroupBy_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.trends(query(FROM, TO, "assignee"), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_GROUP_BY_INVALID.code(), error.getCode());
    }

    @Test
    void trends_invalidRange_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.trends(query("2026-10-05", TO, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID.code(), error.getCode());
    }

    @Test
    void trends_unparsableDate_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.trends(query("10/01", null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID.code(), error.getCode());
    }

    @Test
    void trends_defaultRangeIs30Days() {
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of());

        BugTrendsRespDTO resp = service.trends(new BugAnalysisQueryReqDTO(), PROJECT_ID, USER_ID);

        assertEquals(30, resp.getAxis().size());
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), resp.getAxis().get(29));
    }

    // ---------- 3.3 质量度量 ----------

    @Test
    void metrics_ratesAndDistributionsByCreatedInRange() {
        Bug reopened = bug("active", LocalDateTime.parse("2026-10-01T10:00:00"), null);
        reopened.setReopenCount(1);
        reopened.setSeverity("serious");
        reopened.setBugType("code_error");
        Bug duplicated = bug("resolved", LocalDateTime.parse("2026-10-02T10:00:00"), null);
        duplicated.setSeverity("serious");
        duplicated.setBugType("code_error");
        duplicated.setResolution("duplicate");
        // 区间外新增：不计入分母
        Bug outside = bug("active", LocalDateTime.parse("2026-09-01T10:00:00"), null);
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any()))
                .thenReturn(List.of(reopened, duplicated, outside));

        BugMetricsRespDTO resp = service.metrics(query(FROM, TO, null), PROJECT_ID, USER_ID);

        assertEquals(0.5, resp.getReopenRate());
        assertEquals(0.5, resp.getDuplicateRate());
        assertEquals(1, resp.getSeverityDist().size());
        assertEquals("serious", resp.getSeverityDist().get(0).getKey());
        assertEquals(Long.valueOf(2), resp.getSeverityDist().get(0).getCount());
        assertEquals("未指定模块", resp.getModuleDist().get(0).getKey());
        assertEquals(0, resp.getFixDuration().getSample());
    }

    @Test
    void metrics_emptyDenominator_returnsZero() {
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of());

        BugMetricsRespDTO resp = service.metrics(query(FROM, TO, null), PROJECT_ID, USER_ID);

        assertEquals(0.0, resp.getReopenRate());
        assertEquals(0.0, resp.getDuplicateRate());
        assertTrue(resp.getSeverityDist().isEmpty());
        assertEquals(0, resp.getFixDuration().getSample());
        assertEquals(0.0, resp.getFixDuration().getP50Hours());
    }

    @Test
    void metrics_fixDurationStartsFromLatestReopenBeforeResolve() {
        Bug resolved = bug("resolved", LocalDateTime.parse("2026-10-01T00:00:00"), null);
        resolved.setResolvedAt(LocalDateTime.parse("2026-10-02T00:00:00"));
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of(resolved));
        BugLog reopen = new BugLog();
        reopen.setBugId(resolved.getId());
        reopen.setCreatedAt(LocalDateTime.parse("2026-10-01T12:00:00"));
        when(bugLogMapper.findReopensByBugIds(anyList())).thenReturn(List.of(reopen));

        BugMetricsRespDTO resp = service.metrics(query(FROM, TO, null), PROJECT_ID, USER_ID);

        assertEquals(1, resp.getFixDuration().getSample());
        assertEquals(12.0, resp.getFixDuration().getAvgHours());
        assertEquals(12.0, resp.getFixDuration().getP90Hours());
    }

    @Test
    void metrics_fixDurationPercentileTakesNearestRank() {
        Bug first = bug("resolved", LocalDateTime.parse("2026-10-01T00:00:00"), null);
        first.setResolvedAt(LocalDateTime.parse("2026-10-01T10:00:00"));
        Bug second = bug("resolved", LocalDateTime.parse("2026-10-01T00:00:00"), null);
        second.setResolvedAt(LocalDateTime.parse("2026-10-02T10:00:00"));
        when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of(second, first));
        when(bugLogMapper.findReopensByBugIds(anyList())).thenReturn(List.of());

        BugMetricsRespDTO resp = service.metrics(query(FROM, TO, null), PROJECT_ID, USER_ID);

        assertEquals(2, resp.getFixDuration().getSample());
        assertEquals(22.0, resp.getFixDuration().getAvgHours());
        // 最近秩：p50 取排序后第 1 个（10h），p90 取第 2 个（34h）
        assertEquals(10.0, resp.getFixDuration().getP50Hours());
        assertEquals(34.0, resp.getFixDuration().getP90Hours());
    }

    @Test
    void metrics_rejectsInvalidRange() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.metrics(query("2026-10-10", "2026-10-01", null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID.code(), error.getCode());
    }

    // ---------- 3.8 录入重复检测 ----------

    private static BugDuplicateCheckReqDTO checkReq(String title, String steps, Integer limit) {
        BugDuplicateCheckReqDTO reqDTO = new BugDuplicateCheckReqDTO();
        reqDTO.setTitle(title);
        reqDTO.setSteps(steps);
        reqDTO.setLimit(limit);
        return reqDTO;
    }

    private static AiVectorSearchHitRespDTO hit(UUID bugId, double distance) {
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setEntityId(bugId);
        hit.setEntityType("bug");
        hit.setDistance(distance);
        hit.setContent("登录页点击登录按钮后无任何响应");
        return hit;
    }

    @Test
    void checkDuplicates_blankTitle_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkDuplicates(checkReq("  ", null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID.code(), error.getCode());
    }

    @Test
    void checkDuplicates_limitOutOfRange_rejected() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkDuplicates(checkReq("登录无响应", null, 99), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID.code(), error.getCode());
    }

    @Test
    void checkDuplicates_vectorNotReady_mapsToUnavailable() {
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtilSupplier.unavailable());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkDuplicates(checkReq("登录无响应", null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_RAG_UNAVAILABLE.code(), error.getCode());
    }

    @Test
    void checkDuplicates_mapsHitsAndSkipsDeleted() {
        Bug alive = bug("active", LocalDateTime.parse("2026-10-01T10:00:00"), null);
        alive.setTitle("登录按钮无反应");
        UUID deletedId = UUID.randomUUID();
        when(vectorSearchService.search(any(), eq(List.of(PROJECT_ID)), eq("bug"), eq(5), eq(USER_ID)))
                .thenReturn(List.of(hit(alive.getId(), 0.13), hit(alive.getId(), 0.2),
                        hit(deletedId, 0.3)));
        when(bugMapper.listByIds(List.of(alive.getId(), deletedId))).thenReturn(List.of(alive));

        BugDuplicateCheckRespDTO resp = service.checkDuplicates(
                checkReq("登录无响应", "1. 打开登录页", null), PROJECT_ID, USER_ID);

        assertEquals(1, resp.getList().size());
        BugDuplicateCheckRespDTO.Item item = resp.getList().get(0);
        assertEquals(alive.getId(), item.getBugId());
        assertEquals("登录按钮无反应", item.getTitle());
        assertEquals(0.87, item.getSimilarity());
        assertEquals("登录页点击登录按钮后无任何响应", item.getBasis());
    }

    /** 向量门禁未就绪的两个错误码（1000018119 / 1000018122） */
    private static final class ServiceExceptionUtilSupplier {
        private static ServiceException unavailable() {
            return xyz.migoo.framework.common.exception.ServiceExceptionUtil
                    .get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE);
        }
    }
}
