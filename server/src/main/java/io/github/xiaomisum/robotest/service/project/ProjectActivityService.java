package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectActivityRespDTO;

import java.util.List;
import java.util.UUID;

public interface ProjectActivityService {

    void record(UUID projectId, UUID actorId, String resourceType, UUID resourceId,
                String resourceName, String action, String summary);

    List<ProjectActivityRespDTO> listRecent(UUID projectId, int limit);
}
