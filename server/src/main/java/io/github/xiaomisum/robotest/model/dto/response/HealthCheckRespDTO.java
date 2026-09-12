package io.github.xiaomisum.robotest.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康检查响应（GET /api/health）
 */
@Data
public class HealthCheckRespDTO {

    @Schema(description = "运行状态", example = "UP")
    private String status;

    @Schema(description = "服务器时间", example = "2026-09-01T12:00:00")
    private LocalDateTime timestamp;

    @Schema(description = "版本号", example = "1.0.0")
    private String version;
}