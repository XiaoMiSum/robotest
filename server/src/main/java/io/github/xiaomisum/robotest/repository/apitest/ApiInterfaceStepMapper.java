package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceStep;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiInterfaceStepMapper extends BaseMapperX<ApiInterfaceStep> {

    default List<ApiInterfaceStep> selectListByInterfaceId(UUID interfaceId) {
        return selectList(new LambdaQueryWrapperX<ApiInterfaceStep>()
                .eq(ApiInterfaceStep::getInterfaceId, interfaceId)
                .orderByAsc(ApiInterfaceStep::getSortOrder));
    }

    default void deleteByInterfaceId(UUID interfaceId) {
        delete(new LambdaQueryWrapper<ApiInterfaceStep>()
                .eq(ApiInterfaceStep::getInterfaceId, interfaceId));
    }
}
