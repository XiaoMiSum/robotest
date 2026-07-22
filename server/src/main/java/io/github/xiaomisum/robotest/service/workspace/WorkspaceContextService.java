package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.WorkspaceDefaultProjectReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.WorkspaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.WorkspaceContextRespDTO;

public interface WorkspaceContextService {

    WorkspaceContextRespDTO getWorkspaceContext(String userId, String workspaceId);

    WorkspaceContextRespDTO updateWorkspace(String userId, String workspaceId, WorkspaceUpdateReqDTO reqDTO);

    WorkspaceContextRespDTO setDefaultProject(String userId, String workspaceId, WorkspaceDefaultProjectReqDTO reqDTO);
}
