package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.service.project.TestReviewService;
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
@RequestMapping("/api/project/reviews")
public class TestReviewController {

    @Resource
    private TestReviewService testReviewService;

    @GetMapping
    public Result<PageResult<TestReviewListRespDTO>> getReviewPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(testReviewService.getReviewPage(projectId, status, keyword, pageNo, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TestReviewDetailRespDTO> createReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid TestReviewCreateReqDTO reqDTO) {
        return Result.ok(testReviewService.createReview(projectId, loginUser.getId(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<TestReviewDetailRespDTO> getReviewDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testReviewService.getReviewDetail(id));
    }

    @GetMapping("/{id}/modules")
    public Result<List<TestReviewSnapshotNodeRespDTO>> getReviewSnapshotTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID documentId) {
        return Result.ok(testReviewService.getReviewSnapshotTree(id, documentId));
    }

    @GetMapping("/{id}/module-tree")
    public Result<List<SnapshotModuleTreeRespDTO>> getReviewModuleTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testReviewService.getReviewModuleTree(id));
    }

    @GetMapping("/{id}/cases")
    public Result<List<PlannedCasesRespDTO>> getReviewPlannedCases(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testReviewService.getReviewPlannedCases(id));
    }

    @PutMapping("/{id}/cases")
    public Result<Void> updateReviewCases(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid TestReviewCasesUpdateReqDTO reqDTO) {
        testReviewService.updateReviewCases(id, reqDTO);
        return Result.ok();
    }

    @PostMapping("/{id}/records")
    public Result<Void> submitReviewRecord(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid TestReviewRecordReqDTO reqDTO) {
        testReviewService.submitReviewRecord(id, loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @GetMapping("/{id}/nodes/{nodeId}/records")
    public Result<List<TestReviewRecordRespDTO>> getNodeReviewRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @PathVariable UUID nodeId) {
        return Result.ok(testReviewService.getNodeReviewRecords(id, nodeId));
    }

    @PostMapping("/{id}/complete")
    public Result<Void> completeReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testReviewService.completeReview(id, loginUser.getId());
        return Result.ok();
    }

    @GetMapping("/{id}/progress")
    public Result<TestReviewProgressRespDTO> getReviewProgress(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(testReviewService.getReviewProgress(id));
    }

    @PostMapping("/{id}/sync")
    public Result<Void> syncReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testReviewService.syncReview(id, loginUser.getId());
        return Result.ok();
    }
}
