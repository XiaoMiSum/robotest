package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "initiatorId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TestReview toEntity(TestReviewCreateReqDTO dto);

    default List<UUID> toParticipantIds(List<UUID> participantIds) {
        return participantIds != null ? participantIds : new ArrayList<>();
    }
}
