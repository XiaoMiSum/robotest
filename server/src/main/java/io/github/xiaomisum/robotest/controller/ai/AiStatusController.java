package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

/**
 * 业务端 AI 可用性接口（详设 3.2）：登录即可，无需 ai:admin，仅回三个布尔口径供入口显隐。
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 状态", description = "业务端 AI 可用性（总开关 / 模型就绪 / 入口可见性）")
public class AiStatusController {

    @Resource
    private AiSettingsReader settingsReader;

    @GetMapping("/status")
    public Result<AiStatusRespDTO> status() {
        return Result.ok(settingsReader.status());
    }
}
