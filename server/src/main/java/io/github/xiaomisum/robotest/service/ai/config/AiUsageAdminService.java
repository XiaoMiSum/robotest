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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用量统计与下钻（详设 3.7）：UTC 分组、空区间补 0（前端图表不出现断点）、
 * 区间 ≤ 366 天校验（1000018120）。
 */
@Component
public class AiUsageAdminService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final Set<String> GROUP_BY = Set.of("day", "model", "scene", "callType");

    @Resource
    private AiUsageLogMapper usageLogMapper;
    @Resource
    private AiModelConfigMapper modelMapper;

    /** 聚合（3.7）：summary + series；groupBy = day 时按日补 0 */
    public AiUsageStatisticsRespDTO statistics(AiUsageStatisticsReqDTO req) {
        LocalDate from = req.getFrom() != null ? req.getFrom() : today().minusDays(DEFAULT_RANGE_DAYS);
        LocalDate to = req.getTo() != null ? req.getTo() : today();
        String groupBy = req.getGroupBy() == null ? "day" : req.getGroupBy();
        validate(from, to, groupBy);

        LocalDateTime fromDt = startOfDay(from);
        LocalDateTime toDt = startOfDay(to.plusDays(1));
        AiUsageSummaryRowDTO summaryRow = usageLogMapper.selectSummary(fromDt, toDt);
        List<AiUsageSeriesRowDTO> rows = switch (groupBy) {
            case "model" -> usageLogMapper.selectSeriesByModel(fromDt, toDt);
            case "scene" -> usageLogMapper.selectSeriesByScene(fromDt, toDt);
            case "callType" -> usageLogMapper.selectSeriesByCallType(fromDt, toDt);
            default -> usageLogMapper.selectSeriesByDay(fromDt, toDt);
        };

        AiUsageStatisticsRespDTO resp = new AiUsageStatisticsRespDTO();
        resp.setSummary(toSummary(summaryRow));
        resp.setSeries(buildSeries(groupBy, rows, from, to));
        return resp;
    }

    /** 下钻（3.7）：join 出任务类型与模型名，点击跳任务详情 */
    public PageResult<AiUsageTaskRespDTO> tasks(AiUsagePageReqDTO req) {
        LocalDate from = req.getFrom() != null ? req.getFrom() : today().minusDays(DEFAULT_RANGE_DAYS);
        LocalDate to = req.getTo() != null ? req.getTo() : today();
        validate(from, to, "day");

        LocalDateTime fromDt = startOfDay(from);
        LocalDateTime toDt = startOfDay(to.plusDays(1));
        long total = usageLogMapper.countUsageTasks(fromDt, toDt, req.getModelId(), req.getScene(),
                req.getCallType(), req.getStatus());
        long offset = (long) (req.getPageNo() - 1) * req.getPageSize();
        List<AiUsageTaskRespDTO> list = usageLogMapper.pageUsageTasks(fromDt, toDt, req.getModelId(),
                req.getScene(), req.getCallType(), req.getStatus(), req.getPageSize(), offset);
        return new PageResult<>(list, total);
    }

    // ========== 私有 ==========

    private void validate(LocalDate from, LocalDate to, String groupBy) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0 || days > MAX_RANGE_DAYS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_USAGE_RANGE_INVALID.code(),
                    "时间范围非法：需满足 from ≤ to 且间隔不超过 " + MAX_RANGE_DAYS + " 天");
        }
        if (!GROUP_BY.contains(groupBy)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_USAGE_RANGE_INVALID.code(),
                    "分组维度非法：" + groupBy);
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static LocalDateTime startOfDay(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIN);
    }

    private AiUsageStatisticsRespDTO.Summary toSummary(AiUsageSummaryRowDTO row) {
        AiUsageStatisticsRespDTO.Summary summary = new AiUsageStatisticsRespDTO.Summary();
        long total = row.getTotalCalls() == null ? 0 : row.getTotalCalls();
        long failed = row.getFailedCalls() == null ? 0 : row.getFailedCalls();
        summary.setTotalCalls(total);
        summary.setFailedCalls(failed);
        summary.setSuccessRate(total == 0 ? 0.0
                : BigDecimal.valueOf(total - failed)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                        .doubleValue());
        summary.setTotalTokens(row.getTotalTokens() == null ? 0 : row.getTotalTokens());
        summary.setAvgLatencyMs(row.getAvgLatencyMs() == null ? 0
                : row.getAvgLatencyMs().setScale(0, RoundingMode.HALF_UP).intValue());
        summary.setTotalCost(row.getTotalCost() == null ? BigDecimal.ZERO : row.getTotalCost());
        return summary;
    }

    private List<AiUsageStatisticsRespDTO.SeriesItem> buildSeries(String groupBy,
            List<AiUsageSeriesRowDTO> rows, LocalDate from, LocalDate to) {
        if ("day".equals(groupBy)) {
            return buildDaySeries(rows, from, to);
        }
        Map<String, String> keyNames = resolveKeyNames(groupBy, rows);
        List<AiUsageStatisticsRespDTO.SeriesItem> series = new ArrayList<>(rows.size());
        for (AiUsageSeriesRowDTO row : rows) {
            series.add(toItem(row, keyNames.getOrDefault(row.getKey(), null)));
        }
        return series;
    }

    /** 空区间补 0（3.7 / 4.3：前端图表不出现断点） */
    private List<AiUsageStatisticsRespDTO.SeriesItem> buildDaySeries(List<AiUsageSeriesRowDTO> rows,
            LocalDate from, LocalDate to) {
        Map<String, AiUsageSeriesRowDTO> byKey = rows.stream()
                .collect(Collectors.toMap(AiUsageSeriesRowDTO::getKey, Function.identity(), (a, b) -> a));
        List<AiUsageStatisticsRespDTO.SeriesItem> series = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            AiUsageSeriesRowDTO row = byKey.get(day.toString());
            series.add(row == null ? zeroItem(day.toString()) : toItem(row, null));
        }
        return series;
    }

    /** model / scene 分组的 keyName（详设 3.7：模型名 / 场景名） */
    private Map<String, String> resolveKeyNames(String groupBy, List<AiUsageSeriesRowDTO> rows) {
        Map<String, String> keyNames = new HashMap<>();
        if ("model".equals(groupBy)) {
            Set<UUID> modelIds = rows.stream()
                    .map(AiUsageSeriesRowDTO::getKey)
                    .filter(key -> key != null && !key.isBlank())
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());
            if (modelIds.isEmpty()) {
                return keyNames;
            }
            Map<UUID, String> names = modelMapper.selectBatchIds(modelIds).stream()
                    .collect(Collectors.toMap(AiModelConfig::getId, AiModelConfig::getName, (a, b) -> a));
            for (UUID id : modelIds) {
                keyNames.put(id.toString(), names.get(id));
            }
        } else if ("scene".equals(groupBy)) {
            for (AiUsageSeriesRowDTO row : rows) {
                AiPromptScenes.PromptScene scene = AiPromptScenes.get(row.getKey());
                if (scene != null) {
                    keyNames.put(row.getKey(), scene.name());
                }
            }
        }
        return keyNames;
    }

    private AiUsageStatisticsRespDTO.SeriesItem toItem(AiUsageSeriesRowDTO row, String keyName) {
        AiUsageStatisticsRespDTO.SeriesItem item = new AiUsageStatisticsRespDTO.SeriesItem();
        item.setKey(row.getKey() == null ? "" : row.getKey());
        item.setKeyName(keyName);
        item.setCalls(row.getCalls() == null ? 0 : row.getCalls());
        item.setFailed(row.getFailed() == null ? 0 : row.getFailed());
        item.setTokens(row.getTokens() == null ? 0 : row.getTokens());
        item.setAvgLatencyMs(row.getAvgLatencyMs() == null ? 0
                : row.getAvgLatencyMs().setScale(0, RoundingMode.HALF_UP).intValue());
        item.setCost(row.getCost() == null ? BigDecimal.ZERO : row.getCost());
        return item;
    }

    private AiUsageStatisticsRespDTO.SeriesItem zeroItem(String key) {
        AiUsageStatisticsRespDTO.SeriesItem item = new AiUsageStatisticsRespDTO.SeriesItem();
        item.setKey(key);
        item.setCalls(0);
        item.setFailed(0);
        item.setTokens(0);
        item.setAvgLatencyMs(0);
        item.setCost(BigDecimal.ZERO);
        return item;
    }
}
