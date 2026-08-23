package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 调试记录列表条目（基础设施设计 3.3.1）
 */
@Data
public class ApiDebugRecordItemRespDTO {

    private UUID id;
    private String name;
    private String method;
    private String url;
    /** success / failed / error */
    private String status;
    private Integer responseStatus;
    private Integer durationMs;
    private LocalDateTime executedAt;
}
