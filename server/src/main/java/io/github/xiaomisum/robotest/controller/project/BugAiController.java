package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugDedupReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugSuggestionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugClusteringStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugDedupRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugSuggestionRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.bug.AiBugClusteringService;
import io.github.xiaomisum.robotest.service.ai.bug.AiBugDedupService;
import io.github.xiaomisum.robotest.service.ai.bug.AiBugSuggestionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 缺陷 AI 能力（3.1–3.3）：仅路由 + 参数校验（C2），业务全部在 Service 层
 */
@RestController
@RequestMapping("/api/project/ai/bugs")
public class BugAiController {

    @Resource
    private AiBugSuggestionService aiBugSuggestionService;
    @Resource
    private AiBugDedupService aiBugDedupService;
    @Resource
    private AiBugClusteringService aiBugClusteringService;

    /** 3.1 缺陷表单智能建议（同步） */
    @PostMapping("/suggest")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<AiBugSuggestionRespDTO> suggest(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid @RequestBody AiBugSuggestionReqDTO reqDTO) {
        return Result.ok(aiBugSuggestionService.suggest(loginUser.getId(), workspaceId, projectId, reqDTO));
    }

    /** 3.2 缺陷语义查重（同步检索） */
    @PostMapping("/dedup")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<AiBugDedupRespDTO> dedup(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid @RequestBody AiBugDedupReqDTO reqDTO) {
        return Result.ok(aiBugDedupService.dedup(loginUser.getId(), workspaceId, projectId, reqDTO));
    }

    /** 3.3.1 发起聚类任务 */
    @PostMapping("/clustering")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<AiBugClusteringStartRespDTO> startClustering(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId) {
        return Result.ok(aiBugClusteringService.startClustering(loginUser.getId(), workspaceId, projectId));
    }

    /** 3.3.2 查询最近一次聚类结果（无任务返回 null） */
    @GetMapping("/clustering/latest")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<AiTaskRespDTO> getLatestClustering(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId) {
        return Result.ok(aiBugClusteringService.getLatestClustering(projectId));
    }
}
