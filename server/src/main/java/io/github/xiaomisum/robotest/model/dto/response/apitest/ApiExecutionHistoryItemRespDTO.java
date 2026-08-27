package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 执行历史条目（测试场景详细设计 3.11.1） */
@Data
@Builder
public class ApiExecutionHistoryItemRespDTO {

    private String id;

    private String status;

    private String executionMode;

    private String triggerType;

    private LocalDateTime executedAt;

    private Integer durationMs;

    private String reportId;

    private String pipelineId;

    private String pipelineUrl;

}
