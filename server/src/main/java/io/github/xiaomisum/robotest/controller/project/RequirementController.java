package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementBatchCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementBatchCreateRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/requirements")
public class RequirementController {

    @Resource
    private RequirementService requirementService;

    @GetMapping
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<PageResult<RequirementListRespDTO>> getRequirementPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(requirementService.getPage(loginUser.getActiveProjectId(), keyword, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<RequirementDetailRespDTO> getRequirementDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(requirementService.getDetail(id, loginUser.getActiveProjectId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('requirement:edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<String> createRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid RequirementCreateReqDTO reqDTO) {
        return Result.ok(requirementService.create(loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('requirement:edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RequirementBatchCreateRespDTO> createRequirementsBatch(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid RequirementBatchCreateReqDTO reqDTO) {
        int count = requirementService.createBatch(loginUser.getActiveProjectId(), loginUser.getId(), reqDTO);
        return Result.ok(RequirementBatchCreateRespDTO.of(count));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('requirement:edit')")
    public Result<Void> updateRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid RequirementUpdateReqDTO reqDTO) {
        requirementService.update(id, loginUser.getActiveProjectId(), loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('requirement:edit')")
    public Result<Void> deleteRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        requirementService.delete(id, loginUser.getActiveProjectId(), loginUser.getId());
        return Result.ok();
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('requirement:edit')")
    public Result<Void> archiveRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid RequirementArchiveReqDTO reqDTO) {
        requirementService.archive(id, loginUser.getActiveProjectId(), loginUser.getId(), reqDTO.getArchived());
        return Result.ok();
    }
}
