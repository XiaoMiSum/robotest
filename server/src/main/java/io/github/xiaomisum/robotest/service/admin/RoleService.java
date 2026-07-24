package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleUserRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface RoleService {

    List<RoleTreeRespDTO> getRoleTree();

    String createRole(RoleCreateReqDTO reqDTO);

    RoleRespDTO updateRole(UUID id, RoleUpdateReqDTO reqDTO);

    void deleteRole(UUID id);

    RoleRespDTO getRoleDetail(UUID id);

    PageResult<RoleUserRespDTO> getRoleUsers(UUID id, Integer pageNo, Integer pageSize);

    void addRoleUsers(UUID id, List<UUID> userIds);

    void removeRoleUser(UUID id, UUID userId);

    RoleRespDTO updateRolePermissions(UUID id, RolePermissionsUpdateReqDTO reqDTO);

    List<PermissionTableRespDTO> getPermissionTable();

    List<String> getUserPermissionCodes(UUID userId);
}
