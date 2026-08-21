package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;

import java.util.List;
import java.util.UUID;

public interface ProjectModuleService {

    List<ProjectModuleTreeRespDTO> getModuleTree(UUID projectId, UUID userId, String assetType);

    ProjectModuleTreeRespDTO createModule(UUID projectId, UUID userId, ProjectModuleCreateReqDTO reqDTO);

    ProjectModuleTreeRespDTO updateModule(UUID moduleId, UUID userId, ProjectModuleUpdateReqDTO reqDTO);

    void deleteModule(UUID moduleId, UUID userId);
}
