package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatisticsRespDTO;
import io.github.xiaomisum.robotest.repository.ai.AiInvocationLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 调用量统计（管理端 3.3.4，来源 ai_invocation_log 聚合）
 */
@Service
public class AiStatisticsService {

    @Resource
    private AiInvocationLogMapper invocationLogMapper;

    /**
     * @param groupBy functionType / workspace / day / model，缺省 functionType
     */
    public AiStatisticsRespDTO getStatistics(LocalDate startDate, LocalDate endDate, String groupBy) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        var startTime = start.atStartOfDay();
        var endTime = end.plusDays(1).atStartOfDay();

        AiStatisticsRespDTO resp = new AiStatisticsRespDTO();
        Map<String, Object> totals = invocationLogMapper.aggregateTotals(startTime, endTime);
        resp.setTotalCalls(longValue(totals, "calls"));
        resp.setTotalTokens(longValue(totals, "tokens"));
        resp.setFailedCalls(longValue(totals, "failed"));

        List<Map<String, Object>> rows = switch (groupBy != null ? groupBy : "functionType") {
            case "workspace" -> invocationLogMapper.aggregateByWorkspace(startTime, endTime);
            case "day" -> invocationLogMapper.aggregateByDay(startTime, endTime);
            case "model" -> invocationLogMapper.aggregateByModel(startTime, endTime);
            default -> invocationLogMapper.aggregateByFunctionType(startTime, endTime);
        };
        resp.setItems(rows.stream().map(row -> {
            AiStatisticsRespDTO.Item item = new AiStatisticsRespDTO.Item();
            item.setKey(String.valueOf(row.get("key")));
            item.setCalls(longValue(row, "calls"));
            item.setTokens(longValue(row, "tokens"));
            item.setAvgDurationMs(longValue(row, "avg_duration_ms"));
            item.setFailed(longValue(row, "failed"));
            return item;
        }).toList());
        return resp;
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = row != null ? row.get(key) : null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
