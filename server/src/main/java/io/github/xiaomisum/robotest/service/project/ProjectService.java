package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.ProjectArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ProjectRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

public interface ProjectService {

    PageResult<ProjectRespDTO> getProjectPage(String workspaceId, String userId, String keyword,
                                               String status, Integer pageNo, Integer pageSize);

    ProjectRespDTO getProjectDetail(String workspaceId, String projectId);

    ProjectRespDTO createProject(String userId, String workspaceId, ProjectCreateReqDTO reqDTO);

    ProjectRespDTO updateProject(String userId, String workspaceId, String projectId, ProjectUpdateReqDTO reqDTO);

    void archiveProject(String userId, String workspaceId, String projectId, ProjectArchiveReqDTO reqDTO);

    void deleteProject(String userId, String workspaceId, String projectId);
}
