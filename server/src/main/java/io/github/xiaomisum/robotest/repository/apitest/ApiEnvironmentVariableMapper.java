package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentVariable;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface ApiEnvironmentVariableMapper extends BaseMapperX<ApiEnvironmentVariable> {

    default List<ApiEnvironmentVariable> listByEnvironmentId(UUID environmentId) {
        return selectList(new LambdaQueryWrapperX<ApiEnvironmentVariable>()
                .eq(ApiEnvironmentVariable::getEnvironmentId, environmentId)
                .orderByAsc(ApiEnvironmentVariable::getName));
    }

    default ApiEnvironmentVariable findByEnvironmentIdAndName(UUID environmentId, String name) {
        return selectOne(new LambdaQueryWrapperX<ApiEnvironmentVariable>()
                .eq(ApiEnvironmentVariable::getEnvironmentId, environmentId)
                .eq(ApiEnvironmentVariable::getName, name));
    }

    /** 按环境聚合变量数量，用于环境列表计数列 */
    default Map<UUID, Long> countGroupByEnvironment() {
        return selectMaps(new QueryWrapper<ApiEnvironmentVariable>()
                .select("environment_id", "count(*) AS cnt")
                .groupBy("environment_id")).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row.get("environment_id"),
                        row -> ((Number) row.get("cnt")).longValue()));
    }

    default void deleteByEnvironmentId(UUID environmentId) {
        delete(new LambdaQueryWrapperX<ApiEnvironmentVariable>()
                .eq(ApiEnvironmentVariable::getEnvironmentId, environmentId));
    }
}
