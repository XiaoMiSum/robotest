package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Swagger URL 配置项（定时任务详细设计 3.1.9）
 */
@Data
@Builder
public class ApiSwaggerUrlItemRespDTO {

    private UUID id;
    private String name;
    private String url;
    private String format;
    private String lastImportStatus;
    private LocalDateTime lastImportAt;
    private LocalDateTime createdAt;

}
