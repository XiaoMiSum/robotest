package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiUsagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiUsageStatisticsReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageStatisticsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.config.AiUsageAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

/**
 * 用量统计路由（详设 3.7，仅路由 + 参数校验，C2）；不携带工作空间 / 项目上下文头（C4）。
 */
@RestController
@RequestMapping("/api/ai/usage")
@Tag(name = "AI 用量统计", description = "用量聚合与任务下钻")
public class AiUsageController {

    @Resource
    private AiUsageAdminService usageAdminService;

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiUsageStatisticsRespDTO> statistics(@Valid AiUsageStatisticsReqDTO reqDTO) {
        return Result.ok(usageAdminService.statistics(reqDTO));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<PageResult<AiUsageTaskRespDTO>> tasks(@Valid AiUsagePageReqDTO reqDTO) {
        return Result.ok(usageAdminService.tasks(reqDTO));
    }
}
