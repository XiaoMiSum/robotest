package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestReview;
import io.github.xiaomisum.robotest.model.entity.TestReviewNodeSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mapper
public interface TestReviewConvertMapper {

    TestReviewConvertMapper INSTANCE = Mappers.getMapper(TestReviewConvertMapper.class);

    @Mapping(target = "initiator", ignore = true)
    TestReviewDetailRespDTO toDetailDTO(TestReview review);

    @Mapping(target = "children", ignore = true)
    TestReviewSnapshotNodeRespDTO toSnapshotNodeDTO(TestReviewNodeSnapshot snapshot);

    default List<UUID> toParticipantIds(List<UUID> participantIds) {
        return participantIds != null ? participantIds : new ArrayList<>();
    }
}
