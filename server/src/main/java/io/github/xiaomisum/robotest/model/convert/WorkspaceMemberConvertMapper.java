package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMemberRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkspaceMemberConvertMapper {

    @Mapping(target = "userId", source = "wu.userId")
    @Mapping(target = "workspaceRole", source = "wu.workspaceRole")
    @Mapping(target = "joinedAt", source = "wu.joinedAt")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    WorkspaceMemberRespDTO toRespDTO(WorkspaceUser wu, SysUser user);
}
