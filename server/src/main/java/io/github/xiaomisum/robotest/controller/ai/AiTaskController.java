package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskExecutionCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskCountRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskEventsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskProposalRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * AI 任务资源（总册 3.6，任务 · 执行 · 提案）：仅路由 + 参数校验（C2）。
 * 任务端点不做权限校验（ai:task / ai:confirm 仅前端入口显隐），仅留任务归属可见性（服务层判定）；
 * 上下文经请求头解析后的 LoginUser 传递（C4），不出现在 URL 中。
 */
@RestController
@RequestMapping("/api/ai/tasks")
@Tag(name = "AI 任务", description = "统一任务资源：提交、列表、详情、轨迹、终止 / 再次执行与提案确认")
public class AiTaskController {

    @Resource
    private AiTaskService aiTaskService;

    /** 3.6.2 提交（创建即启动，waitSeconds 同步等待） */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AiTaskRespDTO> submit(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiTaskSubmitReqDTO reqDTO) {
        return Result.ok(aiTaskService.submit(reqDTO, loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), loginUser));
    }

    /** 3.6.3 列表（项目上下文经 X-Active-Project 头过滤，未附带返回本人任务） */
    @GetMapping
    public Result<PageResult<AiTaskRespDTO>> page(@AuthenticationPrincipal LoginUser loginUser,
            @Valid AiTaskPageReqDTO pageReq) {
        return Result.ok(aiTaskService.page(pageReq, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    /** 顶栏指示器轮询（总册 3.6.3）：进行中任务计数（status = running） */
    @GetMapping("/running-count")
    public Result<AiTaskCountRespDTO> runningCount(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(aiTaskService.countActive(loginUser.getActiveProjectId(), loginUser.getId(), loginUser));
    }

    /** 3.6.3 详情（最新执行 + 提案清单摘要 + documentMeta） */
    @GetMapping("/{taskId}")
    public Result<AiTaskRespDTO> getDetail(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId) {
        return Result.ok(aiTaskService.getDetail(taskId, loginUser));
    }

    /** 3.6.3 提案详情（完整内容，键在最新一次执行的提案中定位） */
    @GetMapping("/{taskId}/proposals/{proposalKey}")
    public Result<AiTaskProposalRespDTO> getProposal(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @PathVariable String proposalKey) {
        return Result.ok(aiTaskService.getProposal(taskId, proposalKey, loginUser));
    }

    /** 3.6.4 轨迹查询（afterSeq 游标增量，默认 200 上限 500） */
    @GetMapping("/{taskId}/executions/{executionId}/events")
    public Result<AiTaskEventsRespDTO> getEvents(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @PathVariable UUID executionId,
            @RequestParam(value = "afterSeq", required = false) Integer afterSeq,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return Result.ok(aiTaskService.getEvents(taskId, executionId, afterSeq, pageSize, loginUser));
    }

    /** 3.6.4 终止执行（执行与任务置 aborted，终止原因「用户中止」） */
    @PostMapping("/{taskId}/executions/{executionId}/abort")
    public Result<AiTaskRespDTO> abort(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @PathVariable UUID executionId) {
        return Result.ok(aiTaskService.abort(taskId, executionId, loginUser));
    }

    /** 3.6.4 再次执行（新建执行实例，复用原执行模型） */
    @PostMapping("/{taskId}/executions")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AiTaskRespDTO> reExecute(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @RequestBody(required = false) AiTaskExecutionCreateReqDTO reqDTO) {
        return Result.ok(aiTaskService.reExecute(taskId, reqDTO, loginUser));
    }

    /** 3.6.5 提案确认（批量逐项、逐项事务，整体 200 逐项成败） */
    @PostMapping("/{taskId}/proposals/confirm")
    public Result<AiTaskConfirmRespDTO> confirm(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @RequestBody @Valid AiTaskConfirmReqDTO reqDTO) {
        return Result.ok(aiTaskService.confirm(taskId, reqDTO, loginUser));
    }
}
