package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSwaggerUrlSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSwaggerUrlItemRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiSwaggerUrlService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

/** Swagger URL 配置管理路由（定时任务详细设计 3.1.9） */
@RestController
public class ApiSwaggerUrlController {

    @Resource
    private ApiSwaggerUrlService swaggerUrlService;

    @GetMapping("/api/project/swagger-urls")
    @PreAuthorize("hasAuthority('api-timer:view')")
    public Result<List<ApiSwaggerUrlItemRespDTO>> list(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(value = "name", required = false) String name) {
        return Result.ok(swaggerUrlService.list(workspaceId, projectId, loginUser.getId(), name));
    }

    @PostMapping("/api/project/swagger-urls")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<UUID> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiSwaggerUrlSaveReqDTO reqDTO) {
        return Result.ok(swaggerUrlService.create(workspaceId, projectId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/api/project/swagger-urls/{id}")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiSwaggerUrlSaveReqDTO reqDTO) {
        swaggerUrlService.update(workspaceId, projectId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/swagger-urls/{id}")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        swaggerUrlService.delete(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }
}
