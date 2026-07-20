package io.github.xiaomisum.robotest.service;

import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;

public interface ProjectDashboardService {

    ProjectDashboardRespDTO getDashboard(String projectId);
}
