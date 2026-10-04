package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiEmbeddingSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiEmbeddingRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiEmbeddingTestRespDTO;
import io.github.xiaomisum.robotest.service.ai.config.AiEmbeddingAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

/**
 * 向量 API 配置路由（详设 3.4，单例，仅路由 + 参数校验，C2）。
 */
@RestController
@RequestMapping("/api/ai/embedding")
@Tag(name = "AI 向量配置", description = "向量 API 单例配置查询、保存与连通性测试")
public class AiEmbeddingController {

    @Resource
    private AiEmbeddingAdminService embeddingAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiEmbeddingRespDTO> get() {
        return Result.ok(embeddingAdminService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiEmbeddingRespDTO> save(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiEmbeddingSaveReqDTO reqDTO, HttpServletRequest request) {
        return Result.ok(embeddingAdminService.save(reqDTO, loginUser, request));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('ai:admin')")
    public Result<AiEmbeddingTestRespDTO> test(@AuthenticationPrincipal LoginUser loginUser,
            HttpServletRequest request) {
        return Result.ok(embeddingAdminService.test(loginUser, request));
    }
}
