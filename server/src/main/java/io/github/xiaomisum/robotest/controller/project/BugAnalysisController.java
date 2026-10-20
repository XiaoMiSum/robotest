package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDraftSuggestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;
import io.github.xiaomisum.robotest.service.domain.bug.BugAnalysisService;
import io.github.xiaomisum.robotest.service.domain.bug.BugDraftSuggestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.pojo.Result;

/**
 * 缺陷分析（详设 3.1）：查询与检测入口，统计实时计算；业务逻辑在 Service（C2）。
 */
@RestController
@RequestMapping("/api/project/bugs")
public class BugAnalysisController {

    @Resource
    private BugAnalysisService bugAnalysisService;
    @Resource
    private BugDraftSuggestService bugDraftSuggestService;

    @GetMapping("/analysis/trends")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<BugTrendsRespDTO> getTrends(@AuthenticationPrincipal LoginUser loginUser,
            BugAnalysisQueryReqDTO query) {
        return Result.ok(bugAnalysisService.trends(query, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @GetMapping("/analysis/metrics")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<BugMetricsRespDTO> getMetrics(@AuthenticationPrincipal LoginUser loginUser,
            BugAnalysisQueryReqDTO query) {
        return Result.ok(bugAnalysisService.metrics(query, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @PostMapping("/duplicates/check")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<BugDuplicateCheckRespDTO> checkDuplicates(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody BugDuplicateCheckReqDTO reqDTO) {
        return Result.ok(bugAnalysisService.checkDuplicates(reqDTO,
                loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @PreAuthorize("hasAuthority('bug:view')")
    @Operation(summary = "新建缺陷建议（SSE 流式，详设 3.5）",
            description = "事件流：progress / done / error，每 15s 心跳注释行；"
                    + "流建立前校验失败为同步 HTTP 错误（输入非法 1000018287、无可用模型 1000018118）；"
                    + "须携带 X-Active-Project")
    @ApiResponse(responseCode = "200", description = "SSE 事件流",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string")))
    @PostMapping(value = "/analysis/suggest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suggestDraft(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody BugDraftSuggestReqDTO reqDTO) {
        return bugDraftSuggestService.suggest(reqDTO,
                loginUser.getActiveProjectId(), loginUser.getId());
    }
}
