package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.request.admin.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleWorkspaceUserRespDTO;

import java.util.List;
import java.util.UUID;

public interface RoleService {

    List<RoleSimpleRespDTO> getRoleList(String type);

    String createRole(RoleCreateReqDTO reqDTO);

    RoleRespDTO updateRole(UUID id, RoleUpdateReqDTO reqDTO);

    void deleteRole(UUID id);

    RoleRespDTO getRoleDetail(UUID id);

    List<RoleWorkspaceUserRespDTO> getRoleWorkspaceUsers(UUID roleId);

    void addRoleUsers(UUID id, List<UUID> userIds);

    void addWorkspaceRoleUsers(UUID roleId, List<UUID> userIds, List<UUID> workspaceIds);

    void removeRoleUser(UUID id, UUID userId);

    void removeWorkspaceRoleUser(UUID roleId, UUID userId, UUID workspaceId);

    RoleRespDTO updateRolePermissions(UUID id, RolePermissionsUpdateReqDTO reqDTO);

    List<PermissionTableRespDTO> getPermissionTable(String roleType);

    List<String> getUserPermissionCodes(UUID userId);
}
