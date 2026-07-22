package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.UserRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import xyz.migoo.framework.common.pojo.PageResult;

public interface UserService {

    PageResult<UserRespDTO> getUserPage(String keyword, String status, String roleId, String workspaceId, Integer pageNo, Integer pageSize);

    UserRespDTO getUserDetail(String id);

    String createUser(UserCreateReqDTO reqDTO);

    UserRespDTO updateUser(String id, UserUpdateReqDTO reqDTO);

    UserRespDTO updateUserStatus(String id, String status);

    void batchUpdateStatus(UserBatchStatusReqDTO reqDTO);

    void resetPassword(String id, String newPassword);

    SysUser getUserByUsername(String username);

    SysUser getUserByEmail(String email);
}
