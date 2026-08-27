package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiSwaggerUrlMapper extends BaseMapperX<ApiSwaggerUrl> {

    default List<ApiSwaggerUrl> selectListByProject(UUID projectId, String keyword) {
        return selectList(new LambdaQueryWrapperX<ApiSwaggerUrl>()
                .eq(ApiSwaggerUrl::getProjectId, projectId)
                .likeIfPresent(ApiSwaggerUrl::getName, keyword)
                .orderByDesc(ApiSwaggerUrl::getCreatedAt));
    }
}
