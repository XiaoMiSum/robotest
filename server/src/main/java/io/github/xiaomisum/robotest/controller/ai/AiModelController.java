package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiModelCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiModelUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiModelTestRespDTO;
import io.github.xiaomisum.robotest.service.ai.config.AiModelAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 模型配置路由（详设 3.3，仅路由 + 参数校验，C2）；密钥永不回显。
 */
@RestController
@RequestMapping("/api/ai/models")
@Tag(name = "AI 模型配置", description = "模型 CRUD 与连通性测试")
public class AiModelController {

    @Resource
    private AiModelAdminService modelAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiModelListRespDTO> list() {
        return Result.ok(modelAdminService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiModelRespDTO> create(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiModelCreateReqDTO reqDTO, HttpServletRequest request) {
        return Result.ok(modelAdminService.create(reqDTO, loginUser, request));
    }

    @PutMapping("/{modelId}")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiModelRespDTO> update(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID modelId, @RequestBody @Valid AiModelUpdateReqDTO reqDTO,
            HttpServletRequest request) {
        return Result.ok(modelAdminService.update(modelId, reqDTO, loginUser, request));
    }

    @DeleteMapping("/{modelId}")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable UUID modelId,
            HttpServletRequest request) {
        modelAdminService.delete(modelId, loginUser, request);
        return Result.ok();
    }

    @PostMapping("/{modelId}/test")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiModelTestRespDTO> test(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID modelId, HttpServletRequest request) {
        return Result.ok(modelAdminService.test(modelId, loginUser, request));
    }
}
