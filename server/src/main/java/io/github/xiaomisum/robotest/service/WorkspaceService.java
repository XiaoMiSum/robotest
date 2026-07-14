package io.github.xiaomisum.robotest.service;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

public interface WorkspaceService {

    PageResult<WorkspaceRespDTO> getWorkspacePage(String keyword, Integer pageNo, Integer pageSize);

    String createWorkspace(WorkspaceCreateReqDTO reqDTO);

    WorkspaceRespDTO getWorkspaceDetail(String id);

    WorkspaceRespDTO updateWorkspace(String id, WorkspaceUpdateReqDTO reqDTO);

    void dissolveWorkspace(String id);

    PageResult<WorkspaceMemberRespDTO> getWorkspaceMembers(String id, Integer pageNo, Integer pageSize);

    List<String> addWorkspaceMembers(String id, List<WorkspaceMembersAddReqDTO.MemberItem> members);

    void updateWorkspaceMemberRole(String id, String userId, String workspaceRole);

    void removeWorkspaceMember(String id, String userId);
}
