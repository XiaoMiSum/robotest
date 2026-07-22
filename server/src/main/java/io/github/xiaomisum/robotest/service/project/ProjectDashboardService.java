package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;

public interface ProjectDashboardService {

    ProjectDashboardRespDTO getDashboard(String projectId);
}
