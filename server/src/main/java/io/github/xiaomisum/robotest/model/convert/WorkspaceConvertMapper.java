package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.request.workspace.WorkspaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkspaceConvertMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    // created_by 由 Service 从当前登录用户写入，不接受请求体传入
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Workspace toEntity(WorkspaceCreateReqDTO dto);
}
