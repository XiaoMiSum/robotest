package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.List;
import java.util.UUID;

public interface ApiEnvironmentHttpMapper extends BaseMapperX<ApiEnvironmentHttp> {

    default List<ApiEnvironmentHttp> listByEnvironmentId(UUID environmentId) {
        return selectList(new LambdaQueryWrapperX<ApiEnvironmentHttp>()
                .eq(ApiEnvironmentHttp::getEnvironmentId, environmentId)
                .orderByDesc(ApiEnvironmentHttp::getIsDefault));
    }

    default void clearDefaultByEnvironmentId(UUID environmentId) {
        update(null, new LambdaUpdateWrapperX<ApiEnvironmentHttp>()
                .eq(ApiEnvironmentHttp::getEnvironmentId, environmentId)
                .eq(ApiEnvironmentHttp::getIsDefault, true)
                .set(ApiEnvironmentHttp::getIsDefault, false));
    }

    default void deleteByEnvironmentId(UUID environmentId) {
        delete(new LambdaQueryWrapperX<ApiEnvironmentHttp>()
                .eq(ApiEnvironmentHttp::getEnvironmentId, environmentId));
    }
}
