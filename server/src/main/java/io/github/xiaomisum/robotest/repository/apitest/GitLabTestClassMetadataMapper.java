package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.GitLabTestClassMetadata;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface GitLabTestClassMetadataMapper extends BaseMapperX<GitLabTestClassMetadata> {

    default PageResult<GitLabTestClassMetadata> selectPageByRepository(UUID repositoryId, Boolean isExecutable,
                                                                       String keyword, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<GitLabTestClassMetadata>()
                .eq(GitLabTestClassMetadata::getRepositoryId, repositoryId)
                .eqIfPresent(GitLabTestClassMetadata::getIsExecutable, isExecutable)
                .likeIfPresent(GitLabTestClassMetadata::getDisplayName, keyword)
                .orderByAsc(GitLabTestClassMetadata::getFullClassName));
    }

    default List<GitLabTestClassMetadata> selectListByRepositoryId(UUID repositoryId) {
        return selectList(new LambdaQueryWrapperX<GitLabTestClassMetadata>()
                .eq(GitLabTestClassMetadata::getRepositoryId, repositoryId));
    }

    default int deleteByRepository(UUID repositoryId) {
        return delete(new LambdaQueryWrapperX<GitLabTestClassMetadata>()
                .eq(GitLabTestClassMetadata::getRepositoryId, repositoryId));
    }
}
