package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.tcase.DocumentRequirementsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;
import io.github.xiaomisum.robotest.service.trace.DocumentRequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

/**
 * 文档关联需求接口（脑图详设 4.3）：仅路由 + 参数校验（C2），
 * 查询走 case:view、保存走 case:edit，上下文经请求头解析（C4）。
 */
@RestController
@RequestMapping("/api/project/documents")
@Tag(name = "文档关联需求", description = "脑图文档与需求条目的关联维护，底层经追溯边承载")
public class DocumentRequirementController {

    @Resource
    private DocumentRequirementService documentRequirementService;

    @GetMapping("/{docId}/requirements")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<List<RequirementSummaryRespDTO>> getRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID docId) {
        return Result.ok(documentRequirementService.list(loginUser.getActiveProjectId(), docId));
    }

    @PutMapping("/{docId}/requirements")
    @PreAuthorize("hasAuthority('case:edit')")
    public Result<List<RequirementSummaryRespDTO>> setRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID docId,
            @RequestBody @Valid DocumentRequirementsUpdateReqDTO reqDTO) {
        return Result.ok(documentRequirementService.save(
                loginUser.getActiveProjectId(), docId, reqDTO.getRequirementIds(), loginUser.getId()));
    }
}
