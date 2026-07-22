package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.UserRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UserConvertMapper {

    UserConvertMapper INSTANCE = Mappers.getMapper(UserConvertMapper.class);

    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "workspaces", ignore = true)
    UserRespDTO toRespDTO(SysUser user);

    default UserRespDTO.RoleSimple toRoleSimple(SysRole role) {
        if (role == null) {
            return null;
        }
        UserRespDTO.RoleSimple simple = new UserRespDTO.RoleSimple();
        simple.setId(role.getId());
        simple.setName(role.getName());
        simple.setType(role.getType());
        return simple;
    }

    default UserRespDTO.WorkspaceSimple toWorkspaceSimple(Workspace workspace, WorkspaceUser workspaceUser) {
        if (workspace == null) {
            return null;
        }
        UserRespDTO.WorkspaceSimple simple = new UserRespDTO.WorkspaceSimple();
        simple.setId(workspace.getId());
        simple.setName(workspace.getName());
        if (workspaceUser != null) {
            simple.setWorkspaceRole(workspaceUser.getWorkspaceRole());
        }
        return simple;
    }
}
