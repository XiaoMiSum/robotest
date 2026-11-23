package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDraftSuggestReqDTO;
import io.github.xiaomisum.robotest.service.domain.bug.BugDraftSuggestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 缺陷分析（详设 3.1）：新建缺陷建议入口，重复缺陷检测随建议一并返回；业务逻辑在 Service（C2）。
 */
@RestController
@RequestMapping("/api/project/bugs")
public class BugAnalysisController {

    @Resource
    private BugDraftSuggestService bugDraftSuggestService;

    @Operation(summary = "新建缺陷建议（SSE 流式，含重复缺陷检测，详设 3.4）",
            description = "事件流：progress / done / error，每 15s 心跳注释行；"
                    + "done 载荷含字段建议与 duplicates 疑似重复缺陷；"
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
