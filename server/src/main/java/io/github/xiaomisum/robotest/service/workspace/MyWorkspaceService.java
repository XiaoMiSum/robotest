package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.workspace.MyWorkspaceQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyScopeCountsDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface MyWorkspaceService {

    PageResult<WorkspaceMyRespDTO> getMyWorkspaces(UUID userId, MyWorkspaceQueryReqDTO query);

    WorkspaceMyScopeCountsDTO getMyWorkspaceCounts(UUID userId, String keyword);

    default PageResult<WorkspaceMyRespDTO> getMyWorkspacePage(UUID userId, MyWorkspaceQueryReqDTO query) {
        return getMyWorkspaces(userId, query);
    }

    default PageResult<WorkspaceMyRespDTO> getMyWorkspacePage(UUID userId, String keyword, String scope,
                                                               Integer pageNo, Integer pageSize) {
        return getMyWorkspaces(userId, keyword, scope, pageNo, pageSize);
    }

    default PageResult<WorkspaceMyRespDTO> getMyWorkspaces(UUID userId, String keyword, String scope,
                                                              Integer pageNo, Integer pageSize) {
        MyWorkspaceQueryReqDTO query = new MyWorkspaceQueryReqDTO();
        query.setKeyword(keyword);
        query.setScope(scope);
        if (pageNo != null) {
            query.setPageNo(pageNo);
        }
        if (pageSize != null) {
            query.setPageSize(pageSize);
        }
        return getMyWorkspaces(userId, query);
    }

    void setActiveWorkspace(UUID userId, UUID workspaceId);
}
