package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface WorkspaceMemberService {

    PageResult<WorkspaceMemberRespDTO> getMemberPage(UUID workspaceId, String keyword, Integer pageNo, Integer pageSize);

    WorkspaceMemberAddResultRespDTO addMembers(UUID userId, UUID workspaceId, WorkspaceMembersAddReqDTO reqDTO);

    void updateMemberRole(UUID userId, UUID workspaceId, UUID targetUserId, UUID workspaceRole);

    void removeMember(UUID userId, UUID workspaceId, UUID targetUserId);
}
