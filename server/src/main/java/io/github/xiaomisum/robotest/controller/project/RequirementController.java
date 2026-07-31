package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.service.project.RequirementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public Result<PageResult<RequirementListRespDTO>> getRequirementPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(requirementService.getPage(projectId, keyword, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public Result<RequirementDetailRespDTO> getRequirementDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(requirementService.getDetail(id, projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<String> createRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid RequirementCreateReqDTO reqDTO) {
        return Result.ok(requirementService.create(projectId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    public Result<Void> updateRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid RequirementUpdateReqDTO reqDTO) {
        requirementService.update(id, projectId, loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        requirementService.delete(id, projectId, loginUser.getId());
        return Result.ok();
    }
}
