package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAgentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentRespDTO;
import io.github.xiaomisum.robotest.service.ai.chat.AiAgentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai/agents")
public class AiAgentController {

    @Resource
    private AiAgentService aiAgentService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<List<AiAgentRespDTO>> getAgents() {
        return Result.ok(aiAgentService.getAgents());
    }

    @GetMapping("/{functionType}")
    @PreAuthorize("hasAuthority('ai:view')")
    public Result<AiAgentDetailRespDTO> getAgentDetail(@PathVariable String functionType) {
        return Result.ok(aiAgentService.getAgentDetail(functionType));
    }

    @PutMapping("/{functionType}")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<Void> saveAgent(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String functionType,
            @RequestBody @Valid AiAgentSaveReqDTO reqDTO) {
        aiAgentService.saveAgent(functionType, reqDTO, loginUser.getId());
        return Result.ok();
    }

    @DeleteMapping("/{functionType}")
    @PreAuthorize("hasAuthority('ai:edit')")
    public Result<Void> restoreDefault(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String functionType) {
        aiAgentService.restoreDefault(functionType, loginUser.getId());
        return Result.ok();
    }
}
