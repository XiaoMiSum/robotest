package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.UserRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface UserService {

    PageResult<UserRespDTO> getUserPage(String keyword, String status, UUID roleId, UUID workspaceId, Integer pageNo, Integer pageSize);

    UserRespDTO getUserDetail(UUID id);

    String createUser(UserCreateReqDTO reqDTO);

    UserRespDTO updateUser(UUID id, UserUpdateReqDTO reqDTO);

    UserRespDTO updateUserStatus(UUID id, String status);

    void batchUpdateStatus(UserBatchStatusReqDTO reqDTO);

    void resetPassword(UUID id, String newPassword);

    SysUser getUserByUsername(String username);

    SysUser getUserByEmail(String email);
}
