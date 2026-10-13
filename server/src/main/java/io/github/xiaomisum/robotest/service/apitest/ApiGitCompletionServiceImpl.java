package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitBatchCompletionReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CI 回报批次完成实现（GitLab 集成详细设计 4.6）。
 */
@Service
public class ApiGitCompletionServiceImpl implements ApiGitCompletionService {

    /** CI 回报状态（详设 4.6）：CI 侧无取消语义，取消由轮询通道按流水线状态映射 */
    private static final Set<String> CI_TERMINAL_STATUSES = Set.of("success", "failed");
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RUNNING = "running";

    private final ApiExecutionRecordMapper executionRecordMapper;
    private final ApiGitResultCollector resultCollector;

    public ApiGitCompletionServiceImpl(ApiExecutionRecordMapper executionRecordMapper,
            ApiGitResultCollector resultCollector) {
        this.executionRecordMapper = executionRecordMapper;
        this.resultCollector = resultCollector;
    }

    @Override
    public void reportCompletion(String batchId, ApiGitBatchCompletionReqDTO requestDTO) {
        List<ApiExecutionRecord> records = executionRecordMapper.selectByBatchId(batchId);
        if (records.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND);
        }
        if (!CI_TERMINAL_STATUSES.contains(requestDTO.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED,
                    "非法回报状态：" + requestDTO.getStatus());
        }
        mergeCiCounts(records, requestDTO);
        resultCollector.collectAsync(batchId, ApiGitResultCollector.Channel.COMPLETION,
                requestDTO.getStatus());
    }

    /**
     * CI 自报计数合入批次 summary（详设 4.6「计数记入批次 summary」）。
     * <p>
     * 键以 ci 前缀与平台对账数（expectedCases / actualCases / errorFailed）区分，两侧写入
     * 互不覆盖；逐条条件合入（仅 pending/running），批次已终态整单忽略（BR-1.24）。
     * 与结果回收并发时 summary 以最后写入者为准——键集不相交，先到者的计数保留在
     * 后到者读取的既有值中，无信息丢失。
     */
    private void mergeCiCounts(List<ApiExecutionRecord> records,
            ApiGitBatchCompletionReqDTO requestDTO) {
        Map<String, Object> ciCounts = new LinkedHashMap<>();
        ciCounts.put("ciStatus", requestDTO.getStatus());
        ciCounts.put("ciExpectedCases", requestDTO.getExpectedCases());
        ciCounts.put("ciActualCases", requestDTO.getActualCases());
        if (requestDTO.getPipelineUrl() != null) {
            ciCounts.put("ciPipelineUrl", requestDTO.getPipelineUrl());
        }
        for (ApiExecutionRecord record : records) {
            if (!STATUS_PENDING.equals(record.getStatus()) && !STATUS_RUNNING.equals(record.getStatus())) {
                continue;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            if (record.getSummary() != null) {
                summary.putAll(record.getSummary());
            }
            summary.putAll(ciCounts);
            executionRecordMapper.updateSummaryIfRunning(record.getId(), summary);
        }
    }
}
