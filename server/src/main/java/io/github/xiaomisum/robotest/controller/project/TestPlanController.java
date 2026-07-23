package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.service.project.TestPlanService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

@RestController
@RequestMapping("/api/project/plans")
public class TestPlanController {

    @Resource
    private TestPlanService testPlanService;

    @GetMapping
    public Result<PageResult<TestPlanListRespDTO>> getPlanPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(testPlanService.getPlanPage(projectId, status, pageNo, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TestPlanDetailRespDTO> createPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestBody @Valid TestPlanCreateReqDTO reqDTO) {
        return Result.ok(testPlanService.createPlan(projectId, loginUser.getId().toString(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<TestPlanDetailRespDTO> getPlanDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return Result.ok(testPlanService.getPlanDetail(id));
    }

    @GetMapping("/{id}/modules")
    public Result<List<TestPlanSnapshotNodeRespDTO>> getPlanSnapshotTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestParam(required = false) String documentId) {
        return Result.ok(testPlanService.getPlanSnapshotTree(id, documentId));
    }

    @PostMapping("/{id}/records")
    public Result<Void> submitExecutionRecord(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid TestPlanRecordReqDTO reqDTO) {
        testPlanService.submitExecutionRecord(id, loginUser.getId().toString(), reqDTO);
        return Result.ok();
    }

    @GetMapping("/{id}/nodes/{nodeId}/records")
    public Result<List<TestPlanExecutionRecordRespDTO>> getNodeExecutionRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @PathVariable String nodeId) {
        return Result.ok(testPlanService.getNodeExecutionRecords(id, nodeId));
    }

    @PostMapping("/{id}/sync")
    public Result<Void> syncPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testPlanService.syncPlan(id, loginUser.getId().toString());
        return Result.ok();
    }

    @PostMapping("/{id}/start")
    public Result<Void> startPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testPlanService.startPlan(id, loginUser.getId().toString());
        return Result.ok();
    }

    @GetMapping("/{id}/progress")
    public Result<TestPlanProgressRespDTO> getPlanProgress(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return Result.ok(testPlanService.getPlanProgress(id));
    }

    @PostMapping("/{id}/close")
    public Result<Void> closePlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testPlanService.closePlan(id, loginUser.getId().toString());
        return Result.ok();
    }
}
