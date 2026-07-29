package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserSimpleRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface UserService {

    PageResult<UserRespDTO> getUserPage(String keyword, String status, UUID roleId, Integer pageNo, Integer pageSize);

    List<UserSimpleRespDTO> getUserSimpleList(String keyword);

    UserRespDTO getUserDetail(UUID id);

    String createUser(UserCreateReqDTO reqDTO);

    UserRespDTO updateUser(UUID id, UserUpdateReqDTO reqDTO);

    UserRespDTO updateUserStatus(UUID id, String status);

    void batchUpdateStatus(UserBatchStatusReqDTO reqDTO);

    void resetPassword(UUID id, String newPassword);

    void changePassword(UUID userId, String oldPassword, String newPassword);

    SysUser getUserByUsername(String username);

    SysUser getUserByEmail(String email);
}
