package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface ApiEnvironmentHttpMapper extends BaseMapperX<ApiEnvironmentHttp> {

    default List<ApiEnvironmentHttp> listByEnvironmentId(UUID environmentId) {
        return selectList(new LambdaQueryWrapperX<ApiEnvironmentHttp>()
                .eq(ApiEnvironmentHttp::getEnvironmentId, environmentId)
                .orderByAsc(ApiEnvironmentHttp::getCreatedAt));
    }

    /** 按环境聚合 HTTP 配置数量，用于环境列表计数列 */
    default Map<UUID, Long> countGroupByEnvironment() {
        return selectMaps(new QueryWrapper<ApiEnvironmentHttp>()
                .select("environment_id", "count(*) AS cnt")
                .groupBy("environment_id")).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row.get("environment_id"),
                        row -> ((Number) row.get("cnt")).longValue()));
    }

    default void deleteByEnvironmentId(UUID environmentId) {
        delete(new LambdaQueryWrapperX<ApiEnvironmentHttp>()
                .eq(ApiEnvironmentHttp::getEnvironmentId, environmentId));
    }
}
