package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiConfigService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/workspace/ai")
public class AiStatusController {

    @Resource
    private AiConfigService aiConfigService;

    @GetMapping("/status")
    public Result<AiStatusRespDTO> getStatus() {
        return Result.ok(aiConfigService.getStatus());
    }
}
