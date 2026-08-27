package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.GitLabSyncHistory;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface GitLabSyncHistoryMapper extends BaseMapperX<GitLabSyncHistory> {

    default List<GitLabSyncHistory> selectListByRepositoryId(UUID repositoryId) {
        return selectList(new LambdaQueryWrapperX<GitLabSyncHistory>()
                .eq(GitLabSyncHistory::getRepositoryId, repositoryId)
                .orderByDesc(GitLabSyncHistory::getSyncAt));
    }
}
