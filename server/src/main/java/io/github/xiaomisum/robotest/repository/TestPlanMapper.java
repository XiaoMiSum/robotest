package io.github.xiaomisum.robotest.repository;

import io.github.xiaomisum.robotest.model.entity.TestPlan;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

@Mapper
public interface TestPlanMapper extends BaseMapperX<TestPlan> {
}
