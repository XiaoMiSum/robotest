package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface WorkspaceMemberConvertMapper {

    WorkspaceMemberConvertMapper INSTANCE = Mappers.getMapper(WorkspaceMemberConvertMapper.class);

    @Mapping(target = "userId", source = "wu.userId")
    @Mapping(target = "workspaceRole", source = "wu.workspaceRole")
    @Mapping(target = "joinedAt", source = "wu.joinedAt")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    WorkspaceMemberRespDTO toRespDTO(WorkspaceUser wu, SysUser user);
}
