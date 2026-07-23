package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.BugAssignReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.service.project.BugService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

@RestController
@RequestMapping("/api/project/bugs")
public class BugController {

    @Resource
    private BugService bugService;

    @GetMapping
    public Result<PageResult<BugListRespDTO>> getBugPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(bugService.getBugPage(projectId, status, severity, priority,
                assigneeId, keyword, pageNo, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<String> createBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestBody @Valid BugCreateReqDTO reqDTO) {
        return Result.ok(bugService.createBug(projectId, loginUser.getId().toString(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<BugDetailRespDTO> getBugDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return Result.ok(bugService.getBugDetail(id));
    }

    @PutMapping("/{id}")
    public Result<Void> updateBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid BugUpdateReqDTO reqDTO) {
        bugService.updateBug(id, loginUser.getId().toString(), reqDTO);
        return Result.ok();
    }

    @GetMapping("/{id}/logs")
    public Result<List<BugLogRespDTO>> getBugLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return Result.ok(bugService.getBugLogs(id));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> changeBugStatus(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid BugStatusChangeReqDTO reqDTO) {
        bugService.changeBugStatus(id, loginUser.getId().toString(),
                reqDTO.getStatus(), reqDTO.getComment());
        return Result.ok();
    }

    @PutMapping("/{id}/assign")
    public Result<Void> assignBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid BugAssignReqDTO reqDTO) {
        bugService.assignBug(id, loginUser.getId().toString(),
                reqDTO.getAssigneeId().toString());
        return Result.ok();
    }

    @GetMapping("/statistics")
    public Result<BugStatisticsRespDTO> getBugStatistics(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId) {
        return Result.ok(bugService.getBugStatistics(projectId));
    }
}
