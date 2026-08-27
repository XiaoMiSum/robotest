package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiMockDefinitionMapper extends BaseMapperX<ApiMockDefinition> {

    /** 管理列表分页：接口/关键字（名称+路径）/启用状态过滤，优先级升序、创建时间倒序 */
    default PageResult<ApiMockDefinition> selectPage(UUID projectId, UUID interfaceId, String search,
                                                     Boolean enabled, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiMockDefinition>()
                .eq(ApiMockDefinition::getProjectId, projectId)
                .eq(interfaceId != null, ApiMockDefinition::getInterfaceId, interfaceId)
                .and(search != null && !search.isBlank(), w -> w
                        .like(ApiMockDefinition::getName, search)
                        .or()
                        .like(ApiMockDefinition::getPath, search))
                .eq(enabled != null, ApiMockDefinition::getEnabled, enabled)
                .orderByAsc(ApiMockDefinition::getPriority)
                .orderByDesc(ApiMockDefinition::getCreatedAt));
    }

    /** 匹配引擎：同项目同方法同路径的启用规则，priority 升序、创建时间先后兜底 */
    default List<ApiMockDefinition> selectEnabledForMatch(UUID projectId, String method, String path) {
        return selectList(new LambdaQueryWrapperX<ApiMockDefinition>()
                .eq(ApiMockDefinition::getProjectId, projectId)
                .eq(ApiMockDefinition::getMethod, method)
                .eq(ApiMockDefinition::getPath, path)
                .eq(ApiMockDefinition::getEnabled, true)
                .orderByAsc(ApiMockDefinition::getPriority)
                .orderByAsc(ApiMockDefinition::getCreatedAt));
    }

    /** 通配路径候选：path 含 * 的启用规则（详细设计 6.1 路由优先级 2） */
    default List<ApiMockDefinition> selectEnabledWildcards(UUID projectId, String method) {
        return selectList(new LambdaQueryWrapperX<ApiMockDefinition>()
                .eq(ApiMockDefinition::getProjectId, projectId)
                .eq(ApiMockDefinition::getMethod, method)
                .like(ApiMockDefinition::getPath, "*")
                .eq(ApiMockDefinition::getEnabled, true)
                .orderByAsc(ApiMockDefinition::getPriority)
                .orderByAsc(ApiMockDefinition::getCreatedAt));
    }

    /** 免登录访问：跨项目精确路径匹配（Mock 地址与真实接口同构，仅域名/端口区分） */
    default List<ApiMockDefinition> selectEnabledForMatch(String method, String path) {
        return selectList(new LambdaQueryWrapperX<ApiMockDefinition>()
                .eq(ApiMockDefinition::getMethod, method)
                .eq(ApiMockDefinition::getPath, path)
                .eq(ApiMockDefinition::getEnabled, true)
                .orderByAsc(ApiMockDefinition::getPriority)
                .orderByAsc(ApiMockDefinition::getCreatedAt));
    }

    default List<ApiMockDefinition> selectEnabledWildcards(String method) {
        return selectList(new LambdaQueryWrapperX<ApiMockDefinition>()
                .eq(ApiMockDefinition::getMethod, method)
                .like(ApiMockDefinition::getPath, "*")
                .eq(ApiMockDefinition::getEnabled, true)
                .orderByAsc(ApiMockDefinition::getPriority)
                .orderByAsc(ApiMockDefinition::getCreatedAt));
    }

    /** 同项目同路径同方法的规则组（启停冲突校验、优先级默认值与移动共用） */
    default List<ApiMockDefinition> selectGroup(UUID projectId, String method, String path) {
        return selectList(new LambdaQueryWrapperX<ApiMockDefinition>()
                .eq(ApiMockDefinition::getProjectId, projectId)
                .eq(ApiMockDefinition::getMethod, method)
                .eq(ApiMockDefinition::getPath, path));
    }

    default Integer selectMaxPriority(UUID projectId, String method, String path) {
        List<ApiMockDefinition> group = selectGroup(projectId, method, path);
        return group.stream()
                .map(ApiMockDefinition::getPriority)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
    }

    @Update("UPDATE api_mock_definition SET hit_count = hit_count + 1, last_hit_at = CURRENT_TIMESTAMP, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int incrementHit(UUID id);

    @Update("UPDATE api_mock_definition SET hit_count = 0, last_hit_at = NULL, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id}")
    int resetHit(UUID id);

}
