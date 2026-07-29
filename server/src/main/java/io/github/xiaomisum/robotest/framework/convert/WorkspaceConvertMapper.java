package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface WorkspaceConvertMapper {

    WorkspaceConvertMapper INSTANCE = Mappers.getMapper(WorkspaceConvertMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Workspace toEntity(WorkspaceCreateReqDTO dto);
}
