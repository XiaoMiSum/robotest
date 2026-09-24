package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectDashboardConvertMapper {

    ProjectDashboardRespDTO.RecentItem toRecentItem(TestReview review);

    @Mapping(target = "title", source = "plan.name")
    ProjectDashboardRespDTO.RecentItem toRecentItemFromPlan(TestPlan plan);

    @Mapping(target = "assignee", ignore = true)
    ProjectDashboardRespDTO.RecentBugItem toRecentBugItem(Bug bug);
}
