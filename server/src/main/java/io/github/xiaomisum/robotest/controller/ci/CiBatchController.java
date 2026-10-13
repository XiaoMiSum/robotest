package io.github.xiaomisum.robotest.controller.ci;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitBatchManifestRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiGitBatchService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

/**
 * CI 侧接口（GitLab 集成详细设计 4.6）：CI 脚本凭只读令牌回调，无用户上下文（C4 不适用）。
 * <p>认证由 {@code CiTokenAuthFilter} 按 ROBOTEST-CI-TOKEN 头执行，本层只做路由。
 */
@RestController
@RequestMapping("/api/ci/batches/{batchId}")
public class CiBatchController {

    @Resource
    private ApiGitBatchService apiGitBatchService;

    /** 取冻结清单快照：幂等，同一 batchId 多次调用返回同一份（详设 4.2 第 3 步） */
    @GetMapping("/manifest")
    public Result<ApiGitBatchManifestRespDTO> manifest(@PathVariable String batchId) {
        return Result.ok(apiGitBatchService.getManifest(batchId));
    }
}
