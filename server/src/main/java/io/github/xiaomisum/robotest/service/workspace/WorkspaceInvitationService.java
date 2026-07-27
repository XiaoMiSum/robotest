package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.InvitationJoinReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationCheckEmailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationJoinRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.InvitationVerifyRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface WorkspaceInvitationService {

    InvitationRespDTO createInvitation(UUID userId, UUID workspaceId, InvitationCreateReqDTO reqDTO);

    PageResult<InvitationRespDTO> getInvitationPage(UUID workspaceId, Integer pageNo, Integer pageSize);

    void revokeInvitation(UUID userId, UUID workspaceId, UUID invitationId);

    InvitationVerifyRespDTO verifyInvitation(String token);

    InvitationCheckEmailRespDTO checkEmail(String token, String email);

    InvitationJoinRespDTO joinByInvitation(InvitationJoinReqDTO reqDTO);
}
