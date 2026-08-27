package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.service.project.ProjectModuleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/modules")
public class ProjectModuleController {

    @Resource
    private ProjectModuleService projectModuleService;

    @GetMapping
    @PreAuthorize("hasAuthority('case:view')")
    public Result<List<ProjectModuleTreeRespDTO>> getModuleTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(required = false) String assetType) {
        return Result.ok(projectModuleService.getModuleTree(projectId, loginUser.getId(), assetType));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('case:edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProjectModuleTreeRespDTO> createModule(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ProjectModuleCreateReqDTO reqDTO) {
        return Result.ok(projectModuleService.createModule(projectId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('case:edit')")
    public Result<ProjectModuleTreeRespDTO> updateModule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ProjectModuleUpdateReqDTO reqDTO) {
        return Result.ok(projectModuleService.updateModule(id, loginUser.getId(), reqDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('case:edit')")
    public Result<Void> deleteModule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        projectModuleService.deleteModule(id, loginUser.getId());
        return Result.ok();
    }
}
