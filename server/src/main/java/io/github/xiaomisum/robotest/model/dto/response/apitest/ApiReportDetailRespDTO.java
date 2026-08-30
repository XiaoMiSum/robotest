package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 报告详情（基础设施详细设计 3.4.2），stepResults 为执行引擎落库的步骤级快照 */
@Data
@Builder
public class ApiReportDetailRespDTO {

    private String id;

    private String sceneId;

    private String sceneName;

    private String executionMode;

    private String status;

    private Map<String, Object> summary;

    private String environmentName;

    private List<Map<String, Object>> stepResults;

    private LocalDateTime createdAt;

}
