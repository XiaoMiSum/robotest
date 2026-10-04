package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiUsagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiUsageStatisticsReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageSeriesRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageSummaryRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiUsageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiUsageAdminServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 3);

    @Mock
    private AiUsageLogMapper usageLogMapper;
    @Mock
    private AiModelConfigMapper modelMapper;

    @InjectMocks
    private AiUsageAdminService service;

    private static AiUsageStatisticsReqDTO statisticsReq(LocalDate from, LocalDate to, String groupBy) {
        AiUsageStatisticsReqDTO req = new AiUsageStatisticsReqDTO();
        req.setFrom(from);
        req.setTo(to);
        req.setGroupBy(groupBy);
        return req;
    }

    private static AiUsageSeriesRowDTO seriesRow(String key, long calls, long failed) {
        AiUsageSeriesRowDTO row = new AiUsageSeriesRowDTO();
        row.setKey(key);
        row.setCalls(calls);
        row.setFailed(failed);
        row.setTokens(100L);
        row.setAvgLatencyMs(new BigDecimal("1500.4"));
        row.setCost(new BigDecimal("0.420000"));
        return row;
    }

    private static AiUsageSummaryRowDTO summaryRow(long total, long failed) {
        AiUsageSummaryRowDTO row = new AiUsageSummaryRowDTO();
        row.setTotalCalls(total);
        row.setFailedCalls(failed);
        row.setTotalTokens(1000L);
        row.setAvgLatencyMs(new BigDecimal("1830.6"));
        row.setTotalCost(new BigDecimal("12.340000"));
        return row;
    }

    private void stubSummary(AiUsageSummaryRowDTO row) {
        lenient().when(usageLogMapper.selectSummary(any(), any())).thenReturn(row);
    }

    @Test
    void statistics_rangeOver366Days_throwsRangeInvalid() {
        AiUsageStatisticsReqDTO req = statisticsReq(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 3), "day");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.statistics(req));

        assertEquals(ErrorCodeConstants.AI_USAGE_RANGE_INVALID.code(), ex.getCode());
    }

    @Test
    void statistics_toBeforeFrom_throwsRangeInvalid() {
        AiUsageStatisticsReqDTO req = statisticsReq(TO, FROM, "day");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.statistics(req));

        assertEquals(ErrorCodeConstants.AI_USAGE_RANGE_INVALID.code(), ex.getCode());
    }

    @Test
    void statistics_invalidGroupBy_throwsRangeInvalid() {
        AiUsageStatisticsReqDTO req = statisticsReq(FROM, TO, "bogus");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.statistics(req));

        assertEquals(ErrorCodeConstants.AI_USAGE_RANGE_INVALID.code(), ex.getCode());
    }

    @Test
    void statistics_daySeries_fillsZeroForEmptyDays() {
        stubSummary(summaryRow(60, 1));
        when(usageLogMapper.selectSeriesByDay(any(), any()))
                .thenReturn(List.of(seriesRow("2026-10-02", 30, 1)));

        AiUsageStatisticsRespDTO dto = service.statistics(statisticsReq(FROM, TO, "day"));

        assertEquals(3, dto.getSeries().size());
        assertEquals(0, dto.getSeries().get(0).getCalls());
        assertEquals("2026-10-01", dto.getSeries().get(0).getKey());
        assertEquals(30, dto.getSeries().get(1).getCalls());
        assertEquals(0, dto.getSeries().get(2).getCalls());
    }

    @Test
    void statistics_summary_computesRateAndRoundsLatency() {
        stubSummary(summaryRow(60, 15));
        when(usageLogMapper.selectSeriesByDay(any(), any())).thenReturn(List.of());

        AiUsageStatisticsRespDTO dto = service.statistics(statisticsReq(FROM, TO, "day"));

        assertEquals(60, dto.getSummary().getTotalCalls());
        assertEquals(15, dto.getSummary().getFailedCalls());
        assertEquals(0.75, dto.getSummary().getSuccessRate());
        assertEquals(1831, dto.getSummary().getAvgLatencyMs());
        assertEquals(0, new BigDecimal("12.340000").compareTo(dto.getSummary().getTotalCost()));
    }

    @Test
    void statistics_emptySummary_successRateZero() {
        stubSummary(summaryRow(0, 0));
        when(usageLogMapper.selectSeriesByDay(any(), any())).thenReturn(List.of());

        AiUsageStatisticsRespDTO dto = service.statistics(statisticsReq(FROM, TO, "day"));

        assertEquals(0.0, dto.getSummary().getSuccessRate());
        assertEquals(3, dto.getSeries().size());
        assertTrue(dto.getSeries().stream().allMatch(item -> item.getCalls() == 0));
    }

    @Test
    void statistics_modelSeries_resolvesKeyNames() {
        stubSummary(summaryRow(10, 0));
        UUID modelId = UUID.randomUUID();
        when(usageLogMapper.selectSeriesByModel(any(), any()))
                .thenReturn(List.of(seriesRow(modelId.toString(), 10, 0)));
        AiModelConfig model = new AiModelConfig();
        model.setId(modelId);
        model.setName("gpt-x");
        when(modelMapper.selectBatchIds(any())).thenReturn(List.of(model));

        AiUsageStatisticsRespDTO dto = service.statistics(statisticsReq(FROM, TO, "model"));

        assertEquals("gpt-x", dto.getSeries().get(0).getKeyName());
        assertEquals(modelId.toString(), dto.getSeries().get(0).getKey());
    }

    @Test
    void statistics_sceneSeries_resolvesRegisteredSceneNames() {
        stubSummary(summaryRow(5, 0));
        when(usageLogMapper.selectSeriesByScene(any(), any()))
                .thenReturn(List.of(seriesRow("requirement_split", 5, 0)));

        AiUsageStatisticsRespDTO dto = service.statistics(statisticsReq(FROM, TO, "scene"));

        assertEquals("需求拆分", dto.getSeries().get(0).getKeyName());
    }

    @Test
    void statistics_defaultRange_recent30Days() {
        stubSummary(summaryRow(1, 0));
        when(usageLogMapper.selectSeriesByDay(any(), any())).thenReturn(List.of());

        AiUsageStatisticsRespDTO dto = service.statistics(new AiUsageStatisticsReqDTO());

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(usageLogMapper).selectSummary(fromCaptor.capture(), toCaptor.capture());
        long days = java.time.temporal.ChronoUnit.DAYS.between(fromCaptor.getValue().toLocalDate(),
                toCaptor.getValue().toLocalDate().minusDays(1));
        assertEquals(30, days);
        assertEquals(31, dto.getSeries().size());
    }

    @Test
    void tasks_pagesWithFilters() {
        AiUsagePageReqDTO req = new AiUsagePageReqDTO();
        req.setFrom(FROM);
        req.setTo(TO);
        req.setPageNo(2);
        req.setPageSize(10);
        req.setStatus("failed");
        req.setCallType("chat");
        UUID modelId = UUID.randomUUID();
        req.setModelId(modelId);

        when(usageLogMapper.countUsageTasks(any(), any(), eq(modelId), isNull(), eq("chat"), eq("failed")))
                .thenReturn(25L);
        AiUsageTaskRespDTO row = new AiUsageTaskRespDTO();
        row.setTaskId(UUID.randomUUID());
        row.setStatus("failed");
        when(usageLogMapper.pageUsageTasks(any(), any(), eq(modelId), isNull(), eq("chat"), eq("failed"),
                eq(10), eq(10L)))
                .thenReturn(List.of(row));

        PageResult<AiUsageTaskRespDTO> result = service.tasks(req);

        assertEquals(25L, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("failed", result.getList().get(0).getStatus());
    }

    @Test
    void tasks_invalidRange_throwsRangeInvalid() {
        AiUsagePageReqDTO req = new AiUsagePageReqDTO();
        req.setFrom(LocalDate.of(2024, 1, 1));
        req.setTo(LocalDate.of(2026, 1, 1));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.tasks(req));

        assertEquals(ErrorCodeConstants.AI_USAGE_RANGE_INVALID.code(), ex.getCode());
    }
}
