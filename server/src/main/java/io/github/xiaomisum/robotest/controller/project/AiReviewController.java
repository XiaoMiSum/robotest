package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiReviewSummaryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewSummaryRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiReviewSummaryService;
import jakarta.annotation.Resource;
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
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/ai/reviews")
public class AiReviewController {

    @Resource
    private AiReviewSummaryService aiReviewSummaryService;

    @PostMapping(value = "/{id}/summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateSummary(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody AiReviewSummaryReqDTO reqDTO) {
        return aiReviewSummaryService.generateSummary(loginUser.getId(), workspaceId, projectId, id, reqDTO);
    }

    @GetMapping("/{id}/summary")
    public Result<AiReviewSummaryRespDTO> getSummary(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(aiReviewSummaryService.getSummary(id, loginUser.getId()));
    }
}
