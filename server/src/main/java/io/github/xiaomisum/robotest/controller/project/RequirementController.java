package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitSubmitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.trace.TraceChainRespDTO;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 需求条目接口（详设 3.x）：仅路由 + 参数校验（C2）。
 */
@RestController
@RequestMapping("/api/project/requirements")
@Tag(name = "需求管理", description = "需求条目 CRUD、状态机、变更与拆解记录")
public class RequirementController {

    @Resource
    private RequirementService requirementService;

    @GetMapping
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<PageResult<RequirementListRespDTO>> getRequirementPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid RequirementPageReqDTO pageReq) {
        return Result.ok(requirementService.page(pageReq, loginUser.getActiveProjectId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<RequirementDetailRespDTO> getRequirementDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(requirementService.getDetail(id, loginUser.getActiveProjectId()));
    }

    /** 追溯委托（3.12）：以需求为起点读取追溯链，权限复用需求查看 */
    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<TraceChainRespDTO> getRequirementTrace(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam(required = false) String direction) {
        return Result.ok(requirementService.getTrace(id, loginUser.getActiveProjectId(), direction));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('requirement:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<RequirementDetailRespDTO> createRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid RequirementCreateReqDTO reqDTO) {
        return Result.ok(requirementService.create(loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('requirement:edit')")
    public Result<RequirementDetailRespDTO> updateRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid RequirementUpdateReqDTO reqDTO) {
        return Result.ok(requirementService.update(id, loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('requirement:confirm')")
    public Result<RequirementDetailRespDTO> confirmRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(requirementService.confirm(id, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('requirement:confirm')")
    public Result<RequirementDetailRespDTO> archiveRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(requirementService.archive(id, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasAuthority('requirement:edit')")
    public Result<RequirementDetailRespDTO> unarchiveRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(requirementService.unarchive(id, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @PostMapping("/{id}/split")
    @PreAuthorize("hasAuthority('requirement:edit')")
    public Result<RequirementSplitSubmitRespDTO> splitRequirement(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(requirementService.split(id, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    /** 导入需求文档（3.8）：multipart 提交即返回任务入口，进度与产物确认走 AI 任务资源 */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('requirement:create')")
    public Result<RequirementSplitSubmitRespDTO> importRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(requirementService.importDocument(file, loginUser.getActiveProjectId(),
                loginUser.getId()));
    }

    @GetMapping("/{id}/change-logs")
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<PageResult<RequirementChangeLogRespDTO>> getChangeLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(requirementService.getChangeLogs(id, loginUser.getActiveProjectId(),
                pageParam(pageNo, pageSize)));
    }

    @GetMapping("/{id}/split-logs")
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<PageResult<RequirementSplitRecordRespDTO>> getSplitLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(requirementService.getSplitLogs(id, loginUser.getActiveProjectId(),
                pageParam(pageNo, pageSize)));
    }

    @GetMapping("/split-records")
    @PreAuthorize("hasAuthority('requirement:view')")
    public Result<PageResult<RequirementSplitRecordRespDTO>> getSplitRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(requirementService.getSplitRecords(loginUser.getActiveProjectId(), status, sourceType,
                pageParam(pageNo, pageSize)));
    }

    private static PageParam pageParam(Integer pageNo, Integer pageSize) {
        return new PageParam() {
            {
                setPageNo(pageNo);
                setPageSize(pageSize);
            }
        };
    }
}
