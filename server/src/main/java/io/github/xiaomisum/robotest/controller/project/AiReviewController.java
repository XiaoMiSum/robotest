package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewConclusionReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewSummaryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.review.AiReviewCheckService;
import io.github.xiaomisum.robotest.service.ai.review.AiReviewConclusionService;
import io.github.xiaomisum.robotest.service.ai.review.AiReviewSummaryService;
import jakarta.annotation.Resource;
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
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/ai/reviews")
public class AiReviewController {

    @Resource
    private AiReviewSummaryService aiReviewSummaryService;
    @Resource
    private AiReviewCheckService aiReviewCheckService;
    @Resource
    private AiReviewConclusionService aiReviewConclusionService;

    @PostMapping(value = "/{id}/summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('review:view')")
    public SseEmitter generateSummary(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody AiReviewSummaryReqDTO reqDTO) {
        return aiReviewSummaryService.generateSummary(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), id, reqDTO);
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("hasAuthority('review:view')")
    public Result<AiReviewSummaryRespDTO> getSummary(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(aiReviewSummaryService.getSummary(id, loginUser.getId()));
    }

    @PostMapping("/{id}/check")
    @PreAuthorize("hasAuthority('review:view')")
    public Result<AiReviewCheckStartRespDTO> startCheck(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(aiReviewCheckService.startCheck(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), id));
    }

    @GetMapping("/{id}/check-result")
    @PreAuthorize("hasAuthority('review:view')")
    public Result<AiTaskRespDTO> getCheckResult(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(aiReviewCheckService.getCheckResult(loginUser.getId(), loginUser.getActiveProjectId(), id));
    }

    @PostMapping(value = "/{id}/conclusion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('review:view')")
    public SseEmitter generateConclusion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody(required = false) AiReviewConclusionReqDTO reqDTO) {
        return aiReviewConclusionService.generateConclusion(loginUser.getId(), loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), id, reqDTO);
    }

    @GetMapping("/{id}/conclusion")
    @PreAuthorize("hasAuthority('review:view')")
    public Result<AiTaskRespDTO> getConclusion(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(aiReviewConclusionService.getConclusion(loginUser.getId(), loginUser.getActiveProjectId(), id));
    }
}
