package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.service.TestPlanService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

@RestController
@RequestMapping("/api/project/plans")
public class TestPlanController {

    @Resource
    private TestPlanService testPlanService;

    @GetMapping
    public PageResult<TestPlanListRespDTO> getPlanPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return testPlanService.getPlanPage(projectId, status, pageNo, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestPlanDetailRespDTO createPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestBody @Valid TestPlanCreateReqDTO reqDTO) {
        return testPlanService.createPlan(projectId, loginUser.getId().toString(), reqDTO);
    }

    @GetMapping("/{id}")
    public TestPlanDetailRespDTO getPlanDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return testPlanService.getPlanDetail(id);
    }

    @GetMapping("/{id}/modules")
    public List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestParam(required = false) String documentId) {
        return testPlanService.getPlanSnapshotTree(id, documentId);
    }

    @PostMapping("/{id}/records")
    public void submitExecutionRecord(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid TestPlanRecordReqDTO reqDTO) {
        testPlanService.submitExecutionRecord(id, loginUser.getId().toString(), reqDTO);
    }

    @GetMapping("/{id}/nodes/{nodeId}/records")
    public List<TestPlanExecutionRecordRespDTO> getNodeExecutionRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @PathVariable String nodeId) {
        return testPlanService.getNodeExecutionRecords(id, nodeId);
    }

    @PostMapping("/{id}/sync")
    public void syncPlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testPlanService.syncPlan(id, loginUser.getId().toString());
    }

    @PostMapping("/{id}/close")
    public void closePlan(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testPlanService.closePlan(id, loginUser.getId().toString());
    }
}
