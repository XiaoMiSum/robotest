package io.github.xiaomisum.robotest.service;

import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleUserRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

public interface RoleService {

    List<RoleTreeRespDTO> getRoleTree();

    String createRole(RoleCreateReqDTO reqDTO);

    RoleRespDTO updateRole(String id, RoleUpdateReqDTO reqDTO);

    void deleteRole(String id);

    RoleRespDTO getRoleDetail(String id);

    PageResult<RoleUserRespDTO> getRoleUsers(String id, Integer pageNo, Integer pageSize);

    void addRoleUsers(String id, List<String> userIds);

    void removeRoleUser(String id, String userId);

    RoleRespDTO updateRolePermissions(String id, RolePermissionsUpdateReqDTO reqDTO);

    List<PermissionTableRespDTO> getPermissionTable();

    List<String> getUserPermissionCodes(String userId);
}
