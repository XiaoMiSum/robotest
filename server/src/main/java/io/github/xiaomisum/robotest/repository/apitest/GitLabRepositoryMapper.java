package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.GitLabRepository;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

@Mapper
public interface GitLabRepositoryMapper extends BaseMapperX<GitLabRepository> {

    default PageResult<GitLabRepository> selectPageByProject(UUID projectId, String keyword, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<GitLabRepository>()
                .eq(GitLabRepository::getProjectId, projectId)
                .likeIfPresent(GitLabRepository::getName, keyword)
                .orderByDesc(GitLabRepository::getUpdatedAt));
    }

    default GitLabRepository selectByProjectAndId(UUID projectId, UUID id) {
        return selectOne(new LambdaQueryWrapperX<GitLabRepository>()
                .eq(GitLabRepository::getProjectId, projectId)
                .eq(GitLabRepository::getId, id));
    }

    default boolean existsByProjectAndName(UUID projectId, String name, UUID excludeId) {
        return selectCount(new LambdaQueryWrapperX<GitLabRepository>()
                .eq(GitLabRepository::getProjectId, projectId)
                .eq(GitLabRepository::getName, name)
                .ne(excludeId != null, GitLabRepository::getId, excludeId)) > 0;
    }
}
