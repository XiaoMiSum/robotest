package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.ProjectArchiveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ProjectUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ProjectRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface ProjectService {

    PageResult<ProjectRespDTO> getProjectPage(String workspaceId, UUID userId, String keyword,
                                               String status, Integer pageNo, Integer pageSize);

    ProjectRespDTO getProjectDetail(String workspaceId, UUID projectId);

    ProjectRespDTO createProject(UUID userId, String workspaceId, ProjectCreateReqDTO reqDTO);

    ProjectRespDTO updateProject(UUID userId, String workspaceId, UUID projectId, ProjectUpdateReqDTO reqDTO);

    void archiveProject(UUID userId, String workspaceId, UUID projectId, ProjectArchiveReqDTO reqDTO);

    void deleteProject(UUID userId, String workspaceId, UUID projectId);
}
