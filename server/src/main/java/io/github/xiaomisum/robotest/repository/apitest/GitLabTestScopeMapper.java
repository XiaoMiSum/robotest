package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.GitLabTestScope;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface GitLabTestScopeMapper extends BaseMapperX<GitLabTestScope> {

    default List<GitLabTestScope> selectListByRepositoryId(UUID repositoryId) {
        return selectList(new LambdaQueryWrapperX<GitLabTestScope>()
                .eq(GitLabTestScope::getRepositoryId, repositoryId)
                .orderByAsc(GitLabTestScope::getVariableName));
    }

    default int deleteByRepository(UUID repositoryId) {
        return delete(new LambdaQueryWrapperX<GitLabTestScope>()
                .eq(GitLabTestScope::getRepositoryId, repositoryId));
    }
}
