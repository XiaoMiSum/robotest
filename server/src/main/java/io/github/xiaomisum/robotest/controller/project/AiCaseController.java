package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCasePlanRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPlanOrderReasonReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPriorityRecommendReqDTO;
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
import xyz.migoo.framework.common.pojo.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PostMapping("/cases/priority-recommend")
    public Result<AiPriorityRecommendRespDTO> priorityRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiPriorityRecommendReqDTO reqDTO) {
        return Result.ok(aiPriorityRecommendService.recommend(loginUser.getId(), workspaceId, projectId, reqDTO));
    }

    @PostMapping("/cases/missing-points")
    public Result<AiMissingPointRespDTO> missingPoints(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiMissingPointReqDTO reqDTO) {
        return Result.ok(aiMissingPointService.analyze(loginUser.getId(), workspaceId, projectId, reqDTO));
    }

    @PostMapping("/cases/plan-recommend")
    public Result<AiCasePlanRecommendRespDTO> planRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiCasePlanRecommendReqDTO reqDTO) {
        return Result.ok(aiCasePlanRecommendService.recommend(loginUser.getId(), workspaceId, projectId, reqDTO));
    }

    @PostMapping("/plans/{id}/order-recommend")
    public Result<AiPlanOrderComputeRespDTO> planOrderRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable("id") UUID planId) {
        return Result.ok(aiPlanOrderRecommendService.compute(
                loginUser.getId(), workspaceId, projectId, planId));
    }

    @GetMapping("/plans/{id}/order-recommend")
    public Result<AiPlanOrderQueryRespDTO> planOrderRecommendResult(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable("id") UUID planId) {
        return Result.ok(aiPlanOrderRecommendService.query(
                loginUser.getId(), workspaceId, projectId, planId));
    }

    @PostMapping("/plans/{id}/order-reason")
    public Result<AiPlanOrderReasonRespDTO> planOrderReason(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable("id") UUID planId,
            @RequestBody @Valid AiPlanOrderReasonReqDTO reqDTO) {
        return Result.ok(aiPlanOrderRecommendService.reason(
                loginUser.getId(), workspaceId, projectId, planId, reqDTO));
    }

    @PostMapping(value = "/cases/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiCaseGenerateReqDTO reqDTO) {
        return aiCaseGenerationService.generateCaseTree(loginUser.getId(), workspaceId, projectId, reqDTO);
    }

    @PostMapping(value = "/cases/complete-steps", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completeSteps(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiStepCompleteReqDTO reqDTO) {
        return aiCaseGenerationService.completeSteps(loginUser.getId(), workspaceId, projectId, reqDTO);
    }

    @PostMapping(value = "/minder/import", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importText(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiTextImportReqDTO reqDTO) {
        return aiCaseGenerationService.importText(loginUser.getId(), workspaceId, projectId, reqDTO);
    }
}
