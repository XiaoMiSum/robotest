package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanExecutionRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanProgressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.service.project.TestPlanService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/plans")
public class TestPlanController {

    @Resource
    private TestPlanService testPlanService;

    @GetMapping
    public Result<PageResult<TestPlanListRespDTO>> getPlanPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(testPlanService.getPlanPage(projectId, status, keyword, pageNo, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TestPlanDetailRespDTO> createPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid TestPlanCreateReqDTO reqDTO) {
        return Result.ok(testPlanService.createPlan(projectId, loginUser.getId(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<TestPlanDetailRespDTO> getPlanDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testPlanService.getPlanDetail(id));
    }

    @GetMapping("/{id}/modules")
    public Result<List<TestPlanSnapshotNodeRespDTO>> getPlanSnapshotTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID documentId) {
        return Result.ok(testPlanService.getPlanSnapshotTree(id, documentId));
    }

    @GetMapping("/{id}/module-tree")
    public Result<List<SnapshotModuleTreeRespDTO>> getPlanModuleTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testPlanService.getPlanModuleTree(id));
    }

    @GetMapping("/{id}/cases")
    public Result<List<PlannedCasesRespDTO>> getPlanPlannedCases(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testPlanService.getPlanPlannedCases(id));
    }

    @PutMapping("/{id}/cases")
    public Result<Void> updatePlanCases(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid TestPlanCasesUpdateReqDTO reqDTO) {
        testPlanService.updatePlanCases(id, reqDTO);
        return Result.ok();
    }

    @PostMapping("/{id}/records")
    public Result<Void> submitExecutionRecord(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid TestPlanRecordReqDTO reqDTO) {
        testPlanService.submitExecutionRecord(id, loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @GetMapping("/{id}/nodes/{nodeId}/records")
    public Result<List<TestPlanExecutionRecordRespDTO>> getNodeExecutionRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID nodeId) {
        return Result.ok(testPlanService.getNodeExecutionRecords(id, nodeId));
    }

    @PostMapping("/{id}/sync")
    public Result<Void> syncPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testPlanService.syncPlan(id, loginUser.getId());
        return Result.ok();
    }

    @GetMapping("/{id}/progress")
    public Result<TestPlanProgressRespDTO> getPlanProgress(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testPlanService.getPlanProgress(id));
    }

    @PostMapping("/{id}/close")
    public Result<Void> closePlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testPlanService.closePlan(id, loginUser.getId());
        return Result.ok();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> completePlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testPlanService.completePlan(id, loginUser.getId());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deletePlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testPlanService.deletePlan(id, loginUser.getId());
        return Result.ok();
    }
}
