package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPriorityRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiRegressionRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiStepCompleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTextImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPriorityRecommendRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiRegressionRecommendRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiCaseGenerationService;
import io.github.xiaomisum.robotest.service.ai.AiMissingPointService;
import io.github.xiaomisum.robotest.service.ai.AiPriorityRecommendService;
import io.github.xiaomisum.robotest.service.ai.AiRegressionRecommendService;
import xyz.migoo.framework.common.pojo.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private AiRegressionRecommendService aiRegressionRecommendService;

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

    @PostMapping("/plans/regression-recommend")
    public Result<AiRegressionRecommendRespDTO> regressionRecommend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid AiRegressionRecommendReqDTO reqDTO) {
        return Result.ok(aiRegressionRecommendService.recommend(loginUser.getId(), workspaceId, projectId, reqDTO));
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
