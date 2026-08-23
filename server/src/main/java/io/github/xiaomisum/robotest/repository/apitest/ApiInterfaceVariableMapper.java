package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceVariable;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiInterfaceVariableMapper extends BaseMapperX<ApiInterfaceVariable> {

    default List<ApiInterfaceVariable> selectListByInterfaceId(UUID interfaceId) {
        return selectList(new LambdaQueryWrapperX<ApiInterfaceVariable>()
                .eq(ApiInterfaceVariable::getInterfaceId, interfaceId)
                .orderByAsc(ApiInterfaceVariable::getSortOrder));
    }

    default void deleteByInterfaceId(UUID interfaceId) {
        delete(new LambdaQueryWrapper<ApiInterfaceVariable>()
                .eq(ApiInterfaceVariable::getInterfaceId, interfaceId));
    }
}
