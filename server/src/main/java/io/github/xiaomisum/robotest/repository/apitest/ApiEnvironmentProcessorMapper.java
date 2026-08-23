package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentProcessor;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface ApiEnvironmentProcessorMapper extends BaseMapperX<ApiEnvironmentProcessor> {

    default List<ApiEnvironmentProcessor> listByEnvironmentIdAndType(UUID environmentId, String processorType) {
        return selectList(new LambdaQueryWrapperX<ApiEnvironmentProcessor>()
                .eq(ApiEnvironmentProcessor::getEnvironmentId, environmentId)
                .eqIfPresent(ApiEnvironmentProcessor::getProcessorType, processorType)
                .orderByAsc(ApiEnvironmentProcessor::getSortOrder));
    }

    /** 按环境聚合处理器数量，用于环境列表计数列 */
    default Map<UUID, Long> countGroupByEnvironment() {
        return selectMaps(new QueryWrapper<ApiEnvironmentProcessor>()
                .select("environment_id", "count(*) AS cnt")
                .groupBy("environment_id")).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row.get("environment_id"),
                        row -> ((Number) row.get("cnt")).longValue()));
    }

    default void deleteByEnvironmentId(UUID environmentId) {
        delete(new LambdaQueryWrapperX<ApiEnvironmentProcessor>()
                .eq(ApiEnvironmentProcessor::getEnvironmentId, environmentId));
    }
}
