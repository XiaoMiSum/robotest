package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ProjectArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ProjectRespDTO;
import io.github.xiaomisum.robotest.service.project.ProjectService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/workspace/projects")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @GetMapping
    public Result<PageResult<ProjectRespDTO>> getProjects(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<ProjectRespDTO> result = projectService.getProjectPage(
                workspaceId, loginUser.getId().toString(), keyword, status, pageNo, pageSize);
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<ProjectRespDTO> getProjectDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @PathVariable String id) {
        ProjectRespDTO result = projectService.getProjectDetail(workspaceId, id);
        return Result.ok(result);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProjectRespDTO> createProject(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @RequestBody @Valid ProjectCreateReqDTO reqDTO) {
        ProjectRespDTO result = projectService.createProject(
                loginUser.getId().toString(), workspaceId, reqDTO);
        return Result.ok(result);
    }

    @PutMapping("/{id}")
    public Result<ProjectRespDTO> updateProject(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @PathVariable String id,
            @RequestBody @Valid ProjectUpdateReqDTO reqDTO) {
        ProjectRespDTO result = projectService.updateProject(
                loginUser.getId().toString(), workspaceId, id, reqDTO);
        return Result.ok(result);
    }

    @PostMapping("/{id}/archive")
    public Result<Void> archiveProject(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @PathVariable String id,
            @RequestBody @Valid ProjectArchiveReqDTO reqDTO) {
        projectService.archiveProject(loginUser.getId().toString(), workspaceId, id, reqDTO);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProject(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") String workspaceId,
            @PathVariable String id) {
        projectService.deleteProject(loginUser.getId().toString(), workspaceId, id);
        return Result.ok();
    }
}
