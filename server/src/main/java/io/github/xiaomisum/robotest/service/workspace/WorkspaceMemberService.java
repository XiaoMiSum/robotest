package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface WorkspaceMemberService {

    PageResult<WorkspaceMemberRespDTO> getMemberPage(String workspaceId, String keyword, Integer pageNo, Integer pageSize);

    WorkspaceMemberAddResultRespDTO addMembers(UUID userId, String workspaceId, WorkspaceMembersAddReqDTO reqDTO);

    void updateMemberRole(UUID userId, String workspaceId, UUID targetUserId, UUID workspaceRole);

    void removeMember(UUID userId, String workspaceId, UUID targetUserId);
}
