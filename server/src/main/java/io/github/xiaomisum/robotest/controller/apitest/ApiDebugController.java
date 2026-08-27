package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRecordItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRestoreRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugSaveAsInterfaceRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiDebugService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
public class ApiDebugController {

    @Resource
    private ApiDebugService apiDebugService;

    @PostMapping("/api/project/debug/execute")
    @PreAuthorize("hasAuthority('api-debug:view')")
    public Result<ApiDebugExecuteRespDTO> execute(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiDebugExecuteReqDTO reqDTO) {
        return Result.ok(apiDebugService.execute(projectId, workspaceId, loginUser.getId(), reqDTO));
    }

    @GetMapping("/api/project/debug-records")
    @PreAuthorize("hasAuthority('api-debug:view')")
    public Result<PageResult<ApiDebugRecordItemRespDTO>> pageRecords(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid PageParam pageParam,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(apiDebugService.pageRecords(projectId, workspaceId, loginUser.getId(),
                keyword, pageParam));
    }

    @PutMapping("/api/project/debug-records/{id}")
    @PreAuthorize("hasAuthority('api-debug:view')")
    public Result<Boolean> rename(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiDebugRenameReqDTO reqDTO) {
        apiDebugService.renameRecord(projectId, workspaceId, loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/debug-records/{id}")
    @PreAuthorize("hasAuthority('api-debug:view')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        apiDebugService.deleteRecord(projectId, workspaceId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @GetMapping("/api/project/debug-records/{id}/restore")
    @PreAuthorize("hasAuthority('api-debug:view')")
    public Result<ApiDebugRestoreRespDTO> restore(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(apiDebugService.restore(projectId, workspaceId, loginUser.getId(), id));
    }

    @PostMapping("/api/project/debug-records/{id}/save-as-interface")
    @PreAuthorize("hasAuthority('api-interface:edit')")
    public Result<ApiDebugSaveAsInterfaceRespDTO> saveAsInterface(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody @Valid ApiDebugSaveAsInterfaceReqDTO reqDTO) {
        UUID interfaceId = apiDebugService.saveAsInterface(projectId, workspaceId,
                loginUser.getId(), id, reqDTO);
        return Result.ok(ApiDebugSaveAsInterfaceRespDTO.builder().interfaceId(interfaceId).build());
    }
}
