package io.github.xiaomisum.robotest.repository.project;

import io.github.xiaomisum.robotest.model.entity.project.ProjectSetting;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface ProjectSettingMapper extends BaseMapperX<ProjectSetting> {

    default List<ProjectSetting> listByProjectIdAndDomain(UUID projectId, String domain) {
        return selectList(new LambdaQueryWrapperX<ProjectSetting>()
                .eq(ProjectSetting::getProjectId, projectId)
                .eq(ProjectSetting::getDomain, domain));
    }

    default ProjectSetting findByProjectIdAndDomainAndKey(UUID projectId, String domain, String settingKey) {
        return selectOne(new LambdaQueryWrapperX<ProjectSetting>()
                .eq(ProjectSetting::getProjectId, projectId)
                .eq(ProjectSetting::getDomain, domain)
                .eq(ProjectSetting::getSettingKey, settingKey));
    }
}
