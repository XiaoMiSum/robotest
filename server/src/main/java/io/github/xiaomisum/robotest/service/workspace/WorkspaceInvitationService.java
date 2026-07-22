package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationVerifyRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

public interface WorkspaceInvitationService {

    InvitationRespDTO createInvitation(String userId, String workspaceId, InvitationCreateReqDTO reqDTO);

    PageResult<InvitationRespDTO> getInvitationPage(String workspaceId, Integer pageNo, Integer pageSize);

    void revokeInvitation(String userId, String workspaceId, String invitationId);

    InvitationVerifyRespDTO verifyInvitation(String token);

    InvitationJoinRespDTO joinByInvitation(InvitationJoinReqDTO reqDTO);
}
