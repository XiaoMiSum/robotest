package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.service.BugService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

@RestController
@RequestMapping("/api/project/bugs")
public class BugController {

    @Resource
    private BugService bugService;

    @GetMapping
    public PageResult<BugListRespDTO> getBugPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return bugService.getBugPage(projectId, status, severity, priority,
                assigneeId, keyword, pageNo, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestBody @Valid BugCreateReqDTO reqDTO) {
        return bugService.createBug(projectId, loginUser.getId(), reqDTO);
    }

    @PutMapping("/{id}")
    public void updateBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid BugUpdateReqDTO reqDTO) {
        bugService.updateBug(id, loginUser.getId(), reqDTO);
    }

    @GetMapping("/{id}/logs")
    public List<BugLogRespDTO> getBugLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        return bugService.getBugLogs(id);
    }
}
