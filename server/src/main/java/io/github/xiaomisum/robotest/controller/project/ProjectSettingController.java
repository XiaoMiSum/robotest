package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.project.ProjectSettingUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.project.ProjectSettingListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.project.ProjectSettingUpdateRespDTO;
import io.github.xiaomisum.robotest.service.project.ProjectSettingService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/settings")
public class ProjectSettingController {

    @Resource
    private ProjectSettingService projectSettingService;

    @GetMapping
    public Result<ProjectSettingListRespDTO> getSettings(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam String domain) {
        return Result.ok(projectSettingService.getSettings(projectId, workspaceId, loginUser.getId(), domain));
    }

    @PutMapping
    public Result<ProjectSettingUpdateRespDTO> updateSettings(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ProjectSettingUpdateReqDTO reqDTO) {
        int updated = projectSettingService.updateSettings(projectId, workspaceId, loginUser.getId(), reqDTO);
        return Result.ok(new ProjectSettingUpdateRespDTO(updated));
    }
}
