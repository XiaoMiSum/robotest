package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneFollow;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiSceneFollowMapper extends BaseMapperX<ApiSceneFollow> {

    default ApiSceneFollow selectBySceneAndUser(UUID sceneId, UUID userId) {
        return selectOne(new LambdaQueryWrapper<ApiSceneFollow>()
                .eq(ApiSceneFollow::getSceneId, sceneId)
                .eq(ApiSceneFollow::getUserId, userId)
                .last("LIMIT 1"));
    }

    default List<UUID> selectFollowedSceneIdsByUserId(UUID userId) {
        List<Object> objs = selectObjs(new LambdaQueryWrapperX<ApiSceneFollow>()
                .select(ApiSceneFollow::getSceneId)
                .eq(ApiSceneFollow::getUserId, userId));
        return objs.stream().map(obj -> (UUID) obj).toList();
    }

    default void deleteBySceneId(UUID sceneId) {
        delete(new LambdaQueryWrapperX<ApiSceneFollow>()
                .eq(ApiSceneFollow::getSceneId, sceneId));
    }

}
