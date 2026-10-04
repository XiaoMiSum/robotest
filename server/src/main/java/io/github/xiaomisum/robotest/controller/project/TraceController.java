package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceChainReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceCoveragePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceCoveragePatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceEdgeCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceEdgePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceEdgePatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceGapPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceImpactItemPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceImpactPatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.trace.TraceMatrixPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceChainRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceCoverageRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceEdgeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceGapRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceImpactItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceMatrixItemRespDTO;
import io.github.xiaomisum.robotest.service.trace.TraceMatrixService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 追溯矩阵接口（追溯矩阵详设 3.2–3.10）：仅路由 + 参数校验（C2），
 * 上下文经请求头解析（C4），URL 只承载资源自身标识。
 */
@RestController
@RequestMapping("/api/project/trace")
@Tag(name = "追溯矩阵", description = "矩阵 / 链路 / 追溯边 / 覆盖结论 / 缺口 / 受影响项")
public class TraceController {

    @Resource
    private TraceMatrixService traceMatrixService;

    // ---------- 3.2 矩阵视图 ----------

    @GetMapping("/matrix")
    @PreAuthorize("hasAuthority('trace:view')")
    public Result<PageResult<TraceMatrixItemRespDTO>> getMatrix(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid TraceMatrixPageReqDTO req) {
        return Result.ok(traceMatrixService.matrix(req, loginUser.getActiveProjectId()));
    }

    // ---------- 3.3 链路视图 ----------

    @GetMapping("/chain")
    @PreAuthorize("hasAuthority('trace:view')")
    public Result<TraceChainRespDTO> getChain(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid TraceChainReqDTO req) {
        return Result.ok(traceMatrixService.chain(req, loginUser.getActiveProjectId()));
    }

    // ---------- 3.4 追溯边列表 ----------

    @GetMapping("/edges")
    @PreAuthorize("hasAuthority('trace:view')")
    public Result<PageResult<TraceEdgeRespDTO>> getEdges(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid TraceEdgePageReqDTO req) {
        return Result.ok(traceMatrixService.edges(req, loginUser.getActiveProjectId()));
    }

    // ---------- 3.5 人工新建追溯边 ----------

    @PostMapping("/edges")
    @PreAuthorize("hasAuthority('trace:edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TraceEdgeRespDTO> createEdge(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid TraceEdgeCreateReqDTO req) {
        return Result.ok(traceMatrixService.createEdge(req, loginUser.getActiveProjectId(),
                loginUser.getId()));
    }

    // ---------- 3.6 追溯边修正 ----------

    @PatchMapping("/edges/{edgeId}")
    @PreAuthorize("hasAuthority('trace:edit')")
    public Result<TraceEdgeRespDTO> patchEdge(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID edgeId,
            @RequestBody @Valid TraceEdgePatchReqDTO req) {
        return Result.ok(traceMatrixService.patchEdge(edgeId, req, loginUser.getActiveProjectId(),
                loginUser.getId()));
    }

    // ---------- 3.7 覆盖状态查询 ----------

    @GetMapping("/coverage")
    @PreAuthorize("hasAuthority('trace:view')")
    public Result<PageResult<TraceCoverageRespDTO>> getCoverage(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid TraceCoveragePageReqDTO req) {
        return Result.ok(traceMatrixService.coverage(req, loginUser.getActiveProjectId()));
    }

    // ---------- 3.8 覆盖结论人工修正 ----------

    @PatchMapping("/coverage/{requirementId}")
    @PreAuthorize("hasAuthority('trace:edit')")
    public Result<TraceCoverageRespDTO> patchCoverage(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID requirementId,
            @RequestBody @Valid TraceCoveragePatchReqDTO req) {
        return Result.ok(traceMatrixService.patchCoverage(requirementId, req,
                loginUser.getActiveProjectId(), loginUser.getId()));
    }

    // ---------- 3.9 缺口列表 ----------

    @GetMapping("/gaps")
    @PreAuthorize("hasAuthority('trace:view')")
    public Result<PageResult<TraceGapRespDTO>> getGaps(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid TraceGapPageReqDTO req) {
        return Result.ok(traceMatrixService.gaps(req, loginUser.getActiveProjectId()));
    }

    // ---------- 3.10 受影响项查询与处置 ----------

    @GetMapping("/impact-items")
    @PreAuthorize("hasAuthority('trace:view')")
    public Result<PageResult<TraceImpactItemRespDTO>> getImpactItems(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid TraceImpactItemPageReqDTO req) {
        return Result.ok(traceMatrixService.impactItems(req, loginUser.getActiveProjectId()));
    }

    @PatchMapping("/impact-items/{edgeId}")
    @PreAuthorize("hasAuthority('trace:edit')")
    public Result<TraceImpactItemRespDTO> patchImpactItem(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID edgeId,
            @RequestBody @Valid TraceImpactPatchReqDTO req) {
        return Result.ok(traceMatrixService.patchImpact(edgeId, req, loginUser.getActiveProjectId(),
                loginUser.getId()));
    }
}
