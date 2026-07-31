package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.requirement.DocumentRequirementsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;
import io.github.xiaomisum.robotest.service.project.RequirementService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

/**
 * 脑图文档与需求池条目的关联维护（US-AI-004，3.1.5）。
 */
@RestController
@RequestMapping("/api/project/documents")
public class DocumentRequirementController {

    @Resource
    private RequirementService requirementService;

    @GetMapping("/{docId}/requirements")
    public Result<List<RequirementSummaryRespDTO>> getDocumentRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID docId) {
        return Result.ok(requirementService.getDocumentRequirements(docId, projectId));
    }

    @PutMapping("/{docId}/requirements")
    public Result<Void> setDocumentRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID docId,
            @RequestBody DocumentRequirementsUpdateReqDTO reqDTO) {
        requirementService.setDocumentRequirements(docId, projectId, reqDTO.getRequirementIds());
        return Result.ok();
    }
}
