package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugTrendSummaryHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID BUG_ID = UUID.randomUUID();
    private static final String FROM = "2026-10-01";
    private static final String TO = "2026-10-03";

    @Mock
    private BugAnalysisService bugAnalysisService;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private BugTrendSummaryHandler handler;

    private Bug cited() {
        Bug bug = new Bug();
        bug.setId(BUG_ID);
        bug.setProjectId(PROJECT_ID);
        bug.setTitle("登录按钮无反应");
        bug.setCreatedAt(LocalDateTime.parse("2026-10-01T10:00:00"));
        return bug;
    }

    private void stubContext(String replyJson) {
        lenient().when(context.getProjectId()).thenReturn(PROJECT_ID);
        lenient().when(context.getUserId()).thenReturn(UUID.randomUUID());
        lenient().when(context.getInput()).thenReturn(Map.of("from", FROM, "to", TO));
        lenient().when(context.prompt(any(), any())).thenReturn("prompt");
        lenient().when(context.chat(any(), any())).thenReturn(new AiChatReply(replyJson, 12, 6));
    }

    private void stubStats() {
        BugTrendsRespDTO trends = new BugTrendsRespDTO();
        trends.setAxis(List.of(FROM, TO));
        trends.setGroupBy("none");
        BugTrendsRespDTO.Series series = new BugTrendsRespDTO.Series();
        series.setKey("all");
        series.setLabel("全部");
        series.setCreated(List.of(1, 0));
        series.setClosed(List.of(0, 1));
        series.setActive(List.of(1, 0));
        trends.setSeries(List.of(series));
        BugMetricsRespDTO metrics = new BugMetricsRespDTO();
        BugMetricsRespDTO.FixDuration fix = new BugMetricsRespDTO.FixDuration();
        fix.setSample(1);
        fix.setAvgHours(12.0);
        fix.setP50Hours(12.0);
        fix.setP90Hours(12.0);
        metrics.setFixDuration(fix);
        metrics.setReopenRate(0.0);
        metrics.setDuplicateRate(0.0);
        metrics.setSeverityDist(List.of());
        metrics.setTypeDist(List.of());
        metrics.setModuleDist(List.of());
        lenient().when(bugAnalysisService.trends(any(BugAnalysisQueryReqDTO.class), eq(PROJECT_ID), any()))
                .thenReturn(trends);
        lenient().when(bugAnalysisService.metrics(any(BugAnalysisQueryReqDTO.class), eq(PROJECT_ID), any()))
                .thenReturn(metrics);
        lenient().when(bugMapper.findForAnalysis(eq(PROJECT_ID), any(), any())).thenReturn(List.of(cited()));
    }

    // ---------- SPI 契约 ----------

    @Test
    void type_isBugTrendSummary() {
        assertEquals("bug_trend_summary", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{statsContext}}"));
    }

    @Test
    void validateInput_invalidGroupBy_throws() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("groupBy", "assignee")));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_GROUP_BY_INVALID.code(), exception.getCode());
    }

    @Test
    void validateInput_invertedRange_throws() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("from", TO, "to", FROM)));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_RANGE_INVALID.code(), exception.getCode());
    }

    @Test
    void checkPermission_withoutBugView_throwsNoPermission() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("ai:task"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
    }

    // ---------- 执行 ----------

    @Test
    void execute_sanitizesCitationsToCitedPool() {
        UUID outsideId = UUID.randomUUID();
        stubContext("""
                {"text":"本期新增 1 个缺陷…","citations":[
                  {"type":"bug","id":"%s","title":"任意标题"},
                  {"type":"bug","id":"%s","title":"引用池外应剔除"}]}
                """.formatted(BUG_ID, outsideId));
        stubStats();

        var result = handler.execute(context);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.result().get("artifacts");
        assertEquals(1, artifacts.size());
        Map<String, Object> artifact = artifacts.get(0);
        assertEquals("summary-1", artifact.get("key"));
        assertEquals("summary", artifact.get("kind"));
        assertEquals("not_applicable", artifact.get("confirmStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) artifact.get("content");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> citations = (List<Map<String, Object>>) content.get("citations");
        assertEquals(1, citations.size());
        assertEquals(BUG_ID.toString(), citations.get(0).get("id"));
        // 标题以引用池内真实缺陷为准，不信任模型回填
        assertEquals("登录按钮无反应", citations.get(0).get("title"));
    }

    @Test
    void execute_missingCitations_failsTask() {
        stubContext("{\"text\":\"本期新增 1 个缺陷…\",\"citations\":[]}");
        stubStats();

        ServiceException exception = assertThrows(ServiceException.class, () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }
}
