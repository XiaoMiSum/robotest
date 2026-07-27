package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleSimpleRespDTO;

import java.util.List;
import java.util.UUID;

public interface RoleService {

    List<RoleSimpleRespDTO> getRoleList(String type);

    String createRole(RoleCreateReqDTO reqDTO);

    RoleRespDTO updateRole(UUID id, RoleUpdateReqDTO reqDTO);

    void deleteRole(UUID id);

    RoleRespDTO getRoleDetail(UUID id);

    void addRoleUsers(UUID id, List<UUID> userIds);

    void removeRoleUser(UUID id, UUID userId);

    RoleRespDTO updateRolePermissions(UUID id, RolePermissionsUpdateReqDTO reqDTO);

    List<PermissionTableRespDTO> getPermissionTable(String roleType);

    List<String> getUserPermissionCodes(UUID userId);
}
