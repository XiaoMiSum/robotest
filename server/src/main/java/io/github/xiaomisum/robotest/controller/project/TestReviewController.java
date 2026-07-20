package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.service.TestReviewService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

@RestController
@RequestMapping("/api/project/reviews")
public class TestReviewController {

    @Resource
    private TestReviewService testReviewService;

    @GetMapping
    public PageResult<TestReviewListRespDTO> getReviewPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return testReviewService.getReviewPage(projectId, status, pageNo, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestReviewDetailRespDTO createReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestBody @Valid TestReviewCreateReqDTO reqDTO) {
        return testReviewService.createReview(projectId, loginUser.getId(), reqDTO);
    }

    @GetMapping("/{id}")
    public TestReviewDetailRespDTO getReviewDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return testReviewService.getReviewDetail(id);
    }

    @GetMapping("/{id}/modules")
    public List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestParam(required = false) String documentId) {
        return testReviewService.getReviewSnapshotTree(id, documentId);
    }

    @PostMapping("/{id}/records")
    public void submitReviewRecord(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid TestReviewRecordReqDTO reqDTO) {
        testReviewService.submitReviewRecord(id, loginUser.getId(), reqDTO);
    }

    @GetMapping("/{id}/nodes/{nodeId}/records")
    public List<TestReviewRecordRespDTO> getNodeReviewRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @PathVariable String nodeId) {
        return testReviewService.getNodeReviewRecords(id, nodeId);
    }

    @PostMapping("/{id}/complete")
    public void completeReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testReviewService.completeReview(id, loginUser.getId());
    }

    @PostMapping("/{id}/sync")
    public void syncReview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testReviewService.syncReview(id, loginUser.getId());
    }
}
