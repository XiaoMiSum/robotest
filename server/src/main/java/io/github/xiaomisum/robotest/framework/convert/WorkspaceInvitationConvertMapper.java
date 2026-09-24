package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface WorkspaceInvitationConvertMapper {

    WorkspaceInvitationConvertMapper INSTANCE = Mappers.getMapper(WorkspaceInvitationConvertMapper.class);

    InvitationRespDTO toRespDTO(WorkspaceInvitation invitation);

    /** 列表项不含 token，脱敏预览和有效状态由 Service 基于运行时状态补充 */
    @Mapping(target = "tokenPreview", ignore = true)
    @Mapping(target = "effectiveStatus", ignore = true)
    InvitationListRespDTO toListRespDTO(WorkspaceInvitation invitation);
}
