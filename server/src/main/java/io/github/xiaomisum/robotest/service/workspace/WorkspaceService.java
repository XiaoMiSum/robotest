package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    PageResult<WorkspaceRespDTO> getWorkspacePage(String keyword, Integer pageNo, Integer pageSize);

    String createWorkspace(WorkspaceCreateReqDTO reqDTO);

    WorkspaceRespDTO getWorkspaceDetail(UUID id);

    WorkspaceRespDTO updateWorkspace(UUID id, WorkspaceUpdateReqDTO reqDTO);

    void dissolveWorkspace(UUID id);

    PageResult<WorkspaceMemberRespDTO> getWorkspaceMembers(UUID id, Integer pageNo, Integer pageSize);

    List<String> addWorkspaceMembers(UUID id, List<WorkspaceMembersAddReqDTO.MemberItem> members);

    void updateWorkspaceMemberRole(UUID id, UUID userId, UUID workspaceRole);

    void removeWorkspaceMember(UUID id, UUID userId);
}
