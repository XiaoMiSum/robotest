package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 报告列表条目（基础设施详细设计 3.4.1）；报告名 = sceneName + createdAt 前端派生 */
@Data
@Builder
public class ApiReportPageItemRespDTO {

    private String id;

    private String sceneId;

    private String sceneName;

    private String executionMode;

    /** success / failed / partial */
    private String status;

    /** {total, passed, failed, skipped, durationMs} */
    private Map<String, Object> summary;

    private String environmentName;

    private LocalDateTime createdAt;

}
