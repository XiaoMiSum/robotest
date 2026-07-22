package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.entity.WorkspaceInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface WorkspaceInvitationConvertMapper {

    WorkspaceInvitationConvertMapper INSTANCE = Mappers.getMapper(WorkspaceInvitationConvertMapper.class);

    InvitationRespDTO toRespDTO(WorkspaceInvitation invitation);
}
