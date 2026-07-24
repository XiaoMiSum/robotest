package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMyRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface MyWorkspaceService {

    PageResult<WorkspaceMyRespDTO> getMyWorkspacePage(UUID userId, Integer pageNo, Integer pageSize);

    void setActiveWorkspace(UUID userId, UUID workspaceId);
}
