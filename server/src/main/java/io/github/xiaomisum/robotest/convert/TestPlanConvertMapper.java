package io.github.xiaomisum.robotest.convert;

import io.github.xiaomisum.robotest.model.dto.response.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestPlan;
import io.github.xiaomisum.robotest.model.entity.TestPlanNodeSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper
public interface TestPlanConvertMapper {

    TestPlanConvertMapper INSTANCE = Mappers.getMapper(TestPlanConvertMapper.class);

    @Mapping(target = "executor", ignore = true)
    TestPlanDetailRespDTO toDetailDTO(TestPlan plan);

    @Mapping(target = "originalNodeId", expression = "java(snapshot.getOriginalNodeId() != null ? java.util.UUID.fromString(snapshot.getOriginalNodeId()) : null)")
    @Mapping(target = "parentId", expression = "java(snapshot.getParentId() != null ? java.util.UUID.fromString(snapshot.getParentId()) : null)")
    @Mapping(target = "lastExecutorId", expression = "java(snapshot.getLastExecutorId() != null ? java.util.UUID.fromString(snapshot.getLastExecutorId()) : null)")
    @Mapping(target = "children", ignore = true)
    TestPlanSnapshotNodeRespDTO toSnapshotNodeDTO(TestPlanNodeSnapshot snapshot);
}
