package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.response.WorkspaceMyRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

public interface MyWorkspaceService {

    PageResult<WorkspaceMyRespDTO> getMyWorkspacePage(String userId, Integer pageNo, Integer pageSize);

    void setActiveWorkspace(String userId, String workspaceId);
}
