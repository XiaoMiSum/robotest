package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TestPlan toEntity(TestPlanCreateReqDTO dto);
}
