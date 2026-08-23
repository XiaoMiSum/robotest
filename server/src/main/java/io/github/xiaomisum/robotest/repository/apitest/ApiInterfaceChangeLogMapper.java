package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceChangeLog;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

@Mapper
public interface ApiInterfaceChangeLogMapper extends BaseMapperX<ApiInterfaceChangeLog> {

    default PageResult<ApiInterfaceChangeLog> selectPageByInterfaceId(UUID interfaceId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiInterfaceChangeLog>()
                .eq(ApiInterfaceChangeLog::getInterfaceId, interfaceId)
                .orderByDesc(ApiInterfaceChangeLog::getChangeVersion));
    }
}
