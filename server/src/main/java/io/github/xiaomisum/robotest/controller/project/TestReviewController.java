package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
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
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(testReviewService.getReviewPage(projectId, status, pageNo, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TestReviewDetailRespDTO> createReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
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
