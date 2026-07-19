package io.github.xiaomisum.robotest.service;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceMembersAddReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberAddResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMemberRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

public interface WorkspaceMemberService {

    PageResult<WorkspaceMemberRespDTO> getMemberPage(String workspaceId, String keyword, Integer pageNo, Integer pageSize);

    WorkspaceMemberAddResultRespDTO addMembers(String userId, String workspaceId, WorkspaceMembersAddReqDTO reqDTO);

    void updateMemberRole(String userId, String workspaceId, String targetUserId, String workspaceRole);

    void removeMember(String userId, String workspaceId, String targetUserId);
}
