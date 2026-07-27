package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceContextRespDTO;

import java.util.UUID;

public interface WorkspaceContextService {

    WorkspaceContextRespDTO getWorkspaceContext(UUID userId, UUID workspaceId);

    WorkspaceContextRespDTO updateWorkspace(UUID userId, UUID workspaceId, WorkspaceUpdateReqDTO reqDTO);

    WorkspaceContextRespDTO setDefaultProject(UUID userId, UUID workspaceId, WorkspaceDefaultProjectReqDTO reqDTO);
}
