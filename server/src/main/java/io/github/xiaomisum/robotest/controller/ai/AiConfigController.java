package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiSettingsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiSettingsRespDTO;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

/**
 * AI 总开关与全局设置路由（详设 3.2，仅路由 + 参数校验，C2）；不携带工作空间 / 项目上下文头（C4）。
 */
@RestController
@RequestMapping("/api/ai/settings")
@Tag(name = "AI 全局配置", description = "总开关与全局设置查询、更新")
public class AiConfigController {

    @Resource
    private AiSettingsAdminService settingsAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiSettingsRespDTO> get() {
        return Result.ok(settingsAdminService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiSettingsRespDTO> update(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiSettingsUpdateReqDTO reqDTO, HttpServletRequest request) {
        return Result.ok(settingsAdminService.update(reqDTO, loginUser, request));
    }
}
