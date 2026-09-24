package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.model.dto.request.workspace.MyWorkspaceQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyPageRespDTO;

import java.util.UUID;

public interface MyWorkspaceService {

    WorkspaceMyPageRespDTO getMyWorkspaces(UUID userId, MyWorkspaceQueryReqDTO query);

    default WorkspaceMyPageRespDTO getMyWorkspacePage(UUID userId, MyWorkspaceQueryReqDTO query) {
        return getMyWorkspaces(userId, query);
    }

    default WorkspaceMyPageRespDTO getMyWorkspacePage(UUID userId, String keyword, String scope,
                                                      Integer pageNo, Integer pageSize) {
        return getMyWorkspaces(userId, keyword, scope, pageNo, pageSize);
    }

    default WorkspaceMyPageRespDTO getMyWorkspaces(UUID userId, String keyword, String scope,
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
