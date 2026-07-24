package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestPlan;
import io.github.xiaomisum.robotest.model.entity.TestPlanNodeSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TestPlanConvertMapper {

    TestPlanConvertMapper INSTANCE = Mappers.getMapper(TestPlanConvertMapper.class);

    @Mapping(target = "executor", ignore = true)
    TestPlanDetailRespDTO toDetailDTO(TestPlan plan);

    @Mapping(target = "children", ignore = true)
    TestPlanSnapshotNodeRespDTO toSnapshotNodeDTO(TestPlanNodeSnapshot snapshot);
}
