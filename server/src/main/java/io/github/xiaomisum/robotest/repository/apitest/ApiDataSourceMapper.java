package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiDataSource;
import java.util.Map;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface ApiDataSourceMapper extends BaseMapperX<ApiDataSource> {

    default List<ApiDataSource> listByEnvironmentId(UUID environmentId) {
        return selectList(new LambdaQueryWrapperX<ApiDataSource>()
                .eq(ApiDataSource::getEnvironmentId, environmentId)
                .orderByAsc(ApiDataSource::getName));
    }

    /** 按环境聚合数据源数量，用于环境列表计数列 */
    default Map<UUID, Long> countGroupByEnvironment() {
        return selectMaps(new QueryWrapper<ApiDataSource>()
                .select("environment_id", "count(*) AS cnt")
                .groupBy("environment_id")).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row.get("environment_id"),
                        row -> ((Number) row.get("cnt")).longValue()));
    }

    default void deleteByEnvironmentId(UUID environmentId) {
        delete(new LambdaQueryWrapperX<ApiDataSource>()
                .eq(ApiDataSource::getEnvironmentId, environmentId));
    }
}
