package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface ApiGitRepoMapper extends BaseMapperX<ApiGitRepo> {

    /** 一项目一仓库（uk_api_git_repo_project），按项目取唯一配置 */
    default ApiGitRepo selectByProject(UUID projectId) {
        return selectOne(new LambdaQueryWrapperX<ApiGitRepo>()
                .eq(ApiGitRepo::getProjectId, projectId));
    }

}
