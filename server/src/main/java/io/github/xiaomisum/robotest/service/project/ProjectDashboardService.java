package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;

import java.util.UUID;

public interface ProjectDashboardService {

    ProjectDashboardRespDTO getDashboard(UUID projectId);
}
