package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiInterfaceMapper extends BaseMapperX<ApiInterface> {

    /** 列表分页：模块/关键词/状态过滤 + 视图（followed 由调用方先解析出 id 集合），按更新时间倒序 */
    default PageResult<ApiInterface> selectPage(UUID projectId, UUID moduleId, String search,
                                                String status, Collection<UUID> viewFilterIds, PageParam pageParam) {
        LambdaQueryWrapperX<ApiInterface> wrapper = new LambdaQueryWrapperX<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .eq(moduleId != null, ApiInterface::getModuleId, moduleId)
                .and(search != null && !search.isBlank(), w -> w
                        .like(ApiInterface::getName, search)
                        .or()
                        .like(ApiInterface::getPath, search))
                .eq(status != null && !status.isBlank(), ApiInterface::getStatus, status)
                .in(viewFilterIds != null, ApiInterface::getId, viewFilterIds)
                .orderByDesc(ApiInterface::getUpdatedAt);
        return selectPage(pageParam, wrapper);
    }

    /** 模块内名称唯一校验（创建/重命名场景） */
    default ApiInterface selectByNameAndModule(UUID projectId, UUID moduleId, String name) {
        return selectOne(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .eq(moduleId == null ? ApiInterface::getProjectId : ApiInterface::getModuleId,
                        moduleId == null ? projectId : moduleId)
                .eq(ApiInterface::getName, name)
                .last("LIMIT 1"));
    }

    /** 导入去重：路径+方法匹配 */
    default ApiInterface selectByPathAndMethod(UUID projectId, String method, String path) {
        return selectOne(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .eq(ApiInterface::getMethod, method)
                .eq(ApiInterface::getPath, path)
                .last("LIMIT 1"));
    }

    default List<ApiInterface> selectListByModule(UUID projectId, UUID moduleId) {
        return selectList(new LambdaQueryWrapperX<ApiInterface>()
                .eq(ApiInterface::getProjectId, projectId)
                .eq(moduleId != null, ApiInterface::getModuleId, moduleId));
    }
}
