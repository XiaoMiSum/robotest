package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStep;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiSceneStepMapper extends BaseMapperX<ApiSceneStep> {

    /** 编排画布顺序：sortOrder 升序、创建先后兜底 */
    default List<ApiSceneStep> listBySceneId(UUID sceneId) {
        return selectList(new LambdaQueryWrapperX<ApiSceneStep>()
                .eq(ApiSceneStep::getSceneId, sceneId)
                .orderByAsc(ApiSceneStep::getSortOrder)
                .orderByAsc(ApiSceneStep::getCreatedAt));
    }

    default Integer selectMaxSortOrder(UUID sceneId) {
        List<ApiSceneStep> steps = listBySceneId(sceneId);
        return steps.stream()
                .map(ApiSceneStep::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
    }

    default void deleteBySceneId(UUID sceneId) {
        delete(new LambdaQueryWrapperX<ApiSceneStep>()
                .eq(ApiSceneStep::getSceneId, sceneId));
    }

    /** 排序落库：按传入顺序重写 sort_order（reorder 全量语义） */
    default void reorder(UUID sceneId, List<UUID> stepIds) {
        int order = 0;
        for (UUID stepId : stepIds) {
            ApiSceneStep update = new ApiSceneStep();
            update.setId(stepId);
            update.setSortOrder(order++);
            updateById(update);
        }
    }

}
