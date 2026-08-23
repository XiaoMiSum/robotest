package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceFollow;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiInterfaceFollowMapper extends BaseMapperX<ApiInterfaceFollow> {

    default ApiInterfaceFollow selectByInterfaceAndUser(UUID interfaceId, UUID userId) {
        return selectOne(new LambdaQueryWrapper<ApiInterfaceFollow>()
                .eq(ApiInterfaceFollow::getInterfaceId, interfaceId)
                .eq(ApiInterfaceFollow::getUserId, userId)
                .last("LIMIT 1"));
    }

    default List<ApiInterfaceFollow> selectListByUserId(UUID userId) {
        return selectList(new LambdaQueryWrapperX<ApiInterfaceFollow>()
                .eq(ApiInterfaceFollow::getUserId, userId));
    }
}
