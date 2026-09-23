package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    PageResult<WorkspaceRespDTO> getWorkspacePage(String keyword, String status, Integer pageNo, Integer pageSize);

    /** creatorId 为当前登录用户 ID（由 Controller 从 @AuthenticationPrincipal 传入，写入 created_by） */
    String createWorkspace(WorkspaceCreateReqDTO reqDTO, UUID creatorId);

    WorkspaceRespDTO getWorkspaceDetail(UUID id);

    WorkspaceRespDTO updateWorkspace(UUID id, WorkspaceUpdateReqDTO reqDTO);

    void dissolveWorkspace(UUID id);

    /** 归档（dissolved）重新启用为活跃；成员行归档期间保留，恢复后原样生效 */
    void restoreWorkspace(UUID id);

    PageResult<WorkspaceMemberRespDTO> getWorkspaceMembers(UUID id, Integer pageNo, Integer pageSize);

    List<String> addWorkspaceMembers(UUID id, List<WorkspaceMembersAddReqDTO.MemberItem> members);

    void updateWorkspaceMemberRole(UUID id, UUID userId, UUID workspaceRole);

    void removeWorkspaceMember(UUID id, UUID userId);
}
