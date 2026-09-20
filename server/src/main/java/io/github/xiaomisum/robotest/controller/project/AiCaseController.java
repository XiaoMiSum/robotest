package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCasePlanRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPlanOrderReasonReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPriorityRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiRequirementSplitReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiStepCompleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTextImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiCasePlanRecommendRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderComputeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderQueryRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPlanOrderReasonRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPriorityRecommendRespDTO;
import io.github.xiaomisum.robotest.service.ai.casegen.AiCaseGenerationService;
import io.github.xiaomisum.robotest.service.ai.casegen.AiMissingPointService;
import io.github.xiaomisum.robotest.service.ai.recommend.AiCasePlanRecommendService;
import io.github.xiaomisum.robotest.service.ai.recommend.AiPlanOrderRecommendService;
import io.github.xiaomisum.robotest.service.ai.recommend.AiPriorityRecommendService;
import io.github.xiaomisum.robotest.service.ai.requirement.AiRequirementSplitService;
import xyz.migoo.framework.common.pojo.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/ai")
public class AiCaseController {

    @Resource
    private AiCaseGenerationService aiCaseGenerationService;

    @Resource
    private AiPriorityRecommendService aiPriorityRecommendService;

    @Resource
    private AiMissingPointService aiMissingPointService;

    @Resource
    private AiCasePlanRecommendService aiCasePlanRecommendService;

    @Resource
    private AiPlanOrderRecommendService aiPlanOrderRecommendService;

    @Resource
    private AiRequirementSplitService aiRequirementSplitService;

    @PostMapping("/cases/priority-recommend")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<AiPriorityRecommendRespDTO> priorityRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiPriorityRecommendReqDTO reqDTO) {
        return Result.ok(aiPriorityRecommendService.recommend(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO));
    }

    @PostMapping("/cases/missing-points")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<AiMissingPointRespDTO> missingPoints(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiMissingPointReqDTO reqDTO) {
        return Result.ok(aiMissingPointService.analyze(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO));
    }

    @PostMapping("/cases/plan-recommend")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<AiCasePlanRecommendRespDTO> planRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiCasePlanRecommendReqDTO reqDTO) {
        return Result.ok(aiCasePlanRecommendService.recommend(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO));
    }

    @PostMapping("/plans/{id}/order-recommend")
    @PreAuthorize("hasAuthority('plan:view')")
    public Result<AiPlanOrderComputeRespDTO> planOrderRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") UUID planId) {
        return Result.ok(aiPlanOrderRecommendService.compute(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), planId));
    }

    @GetMapping("/plans/{id}/order-recommend")
    @PreAuthorize("hasAuthority('plan:view')")
    public Result<AiPlanOrderQueryRespDTO> planOrderRecommendResult(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") UUID planId) {
        return Result.ok(aiPlanOrderRecommendService.query(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), planId));
    }

    @PostMapping("/plans/{id}/order-reason")
    @PreAuthorize("hasAuthority('plan:view')")
    public Result<AiPlanOrderReasonRespDTO> planOrderReason(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("id") UUID planId,
            @RequestBody @Valid AiPlanOrderReasonReqDTO reqDTO) {
        return Result.ok(aiPlanOrderRecommendService.reason(
                loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), planId, reqDTO));
    }

    @PostMapping(value = "/cases/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('case:view')")
    public SseEmitter generate(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiCaseGenerateReqDTO reqDTO) {
        return aiCaseGenerationService.generateCaseTree(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO);
    }

    @PostMapping(value = "/cases/complete-steps", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('case:view')")
    public SseEmitter completeSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiStepCompleteReqDTO reqDTO) {
        return aiCaseGenerationService.completeSteps(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO);
    }

    @PostMapping(value = "/minder/import", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('case:view')")
    public SseEmitter importText(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiTextImportReqDTO reqDTO) {
        return aiCaseGenerationService.importText(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO);
    }

    @PostMapping(value = "/requirements/split", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('requirement:view')")
    public SseEmitter splitRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiRequirementSplitReqDTO reqDTO) {
        return aiRequirementSplitService.split(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), reqDTO);
    }
}
