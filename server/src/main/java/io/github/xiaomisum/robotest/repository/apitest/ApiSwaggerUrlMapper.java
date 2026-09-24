package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

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

    /** 更新 Swagger 配置的调用方字段，保留导入状态等未提交列。 */
    default int updateFieldsById(UUID id, String name, String url, String format) {
        return update(null, new LambdaUpdateWrapperX<ApiSwaggerUrl>()
                .eq(ApiSwaggerUrl::getId, id)
                .set(ApiSwaggerUrl::getName, name)
                .set(ApiSwaggerUrl::getUrl, url)
                .set(ApiSwaggerUrl::getFormat, format));
    }
}
