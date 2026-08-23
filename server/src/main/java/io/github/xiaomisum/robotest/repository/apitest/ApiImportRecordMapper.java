package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

@Mapper
public interface ApiImportRecordMapper extends BaseMapperX<ApiImportRecord> {
}
