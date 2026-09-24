package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.time.UtcTime;
import io.github.xiaomisum.robotest.model.dto.response.HealthCheckRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.time.LocalDateTime;

/**
 * 系统健康检查路由（公开端点，配合 OpenAPI JSON 文档展示）
 */
@RestController
@RequestMapping("/api")
@Tag(name = "系统管理", description = "健康检查与系统信息")
public class HealthCheckController {

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "返回系统运行状态、服务器时间与版本号")
    @ApiResponse(responseCode = "200", description = "系统正常")
    public Result<HealthCheckRespDTO> health() {
        HealthCheckRespDTO dto = new HealthCheckRespDTO();
        dto.setStatus("UP");
        dto.setTimestamp(UtcTime.utcNow());
        dto.setVersion("1.0.0");
        return Result.ok(dto);
    }
}