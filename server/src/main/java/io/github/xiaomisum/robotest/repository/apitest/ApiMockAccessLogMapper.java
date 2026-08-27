package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockAccessLog;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

/** Mock 访问日志 Mapper（按项目清理策略归档，查询仅审计用） */
@Mapper
public interface ApiMockAccessLogMapper extends BaseMapperX<ApiMockAccessLog> {

}
