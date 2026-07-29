package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProjectDashboardConvertMapper {

    ProjectDashboardConvertMapper INSTANCE = Mappers.getMapper(ProjectDashboardConvertMapper.class);

    ProjectDashboardRespDTO.RecentItem toRecentItem(TestReview review);

    @Mapping(target = "title", source = "plan.name")
    ProjectDashboardRespDTO.RecentItem toRecentItemFromPlan(TestPlan plan);

    @Mapping(target = "assignee", ignore = true)
    ProjectDashboardRespDTO.RecentBugItem toRecentBugItem(Bug bug);
}
