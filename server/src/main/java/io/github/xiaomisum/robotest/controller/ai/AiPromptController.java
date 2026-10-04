package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPromptSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptListItemRespDTO;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

/**
 * 场景提示词路由（详设 3.5，仅路由 + 参数校验，C2）。
 */
@RestController
@RequestMapping("/api/ai/prompts")
@Tag(name = "AI 场景提示词", description = "场景提示词列表、详情、保存与重置")
public class AiPromptController {

    @Resource
    private AiPromptAdminService promptAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<List<AiPromptListItemRespDTO>> list() {
        return Result.ok(promptAdminService.list());
    }

    @GetMapping("/{scene}")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiPromptDetailRespDTO> getDetail(@PathVariable String scene) {
        return Result.ok(promptAdminService.get(scene));
    }

    @PutMapping("/{scene}")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiPromptDetailRespDTO> save(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String scene, @RequestBody @Valid AiPromptSaveReqDTO reqDTO,
            HttpServletRequest request) {
        return Result.ok(promptAdminService.save(scene, reqDTO, loginUser, request));
    }

    @PostMapping("/{scene}/reset")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiPromptDetailRespDTO> reset(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String scene, HttpServletRequest request) {
        return Result.ok(promptAdminService.reset(scene, loginUser, request));
    }
}
