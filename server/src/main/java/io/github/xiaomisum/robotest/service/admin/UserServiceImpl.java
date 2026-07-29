package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.UserConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.admin.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.UserSimpleRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserRespDTO> getUserPage(String keyword, String status, UUID roleId,
                                               Integer pageNo, Integer pageSize) {
        List<UUID> filteredUserIds = null;
        if (roleId != null) {
            List<SysUserRole> userRoles = userRoleMapper.listByRoleId(roleId);
            filteredUserIds = userRoles.stream().map(SysUserRole::getUserId).collect(Collectors.toList());
            if (filteredUserIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L);
            }
        }

        PageResult<SysUser> userPage = userMapper.findPage(keyword, status, filteredUserIds, pageNo, pageSize);

        List<UserRespDTO> records = userPage.getList().stream()
                .map(this::convertToUserRespDTO)
                .collect(Collectors.toList());

        return new PageResult<>(records, userPage.getTotal());
    }

    @Override
    public List<UserSimpleRespDTO> getUserSimpleList(String keyword) {
        List<SysUser> users = userMapper.listActiveByKeyword(keyword);
        return users.stream().map(user -> {
            UserSimpleRespDTO dto = new UserSimpleRespDTO();
            dto.setId(user.getId());
            dto.setName(user.getName());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public UserRespDTO getUserDetail(UUID id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        return convertToUserRespDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUser(UserCreateReqDTO reqDTO) {
        if (userMapper.findByUsername(reqDTO.getUsername()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USERNAME_EXISTS);
        }
        if (userMapper.findByEmail(reqDTO.getEmail()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.EMAIL_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(reqDTO.getUsername());
        user.setName(reqDTO.getName());
        user.setEmail(reqDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(reqDTO.getPassword()));
        user.setStatus(Constants.Status.ACTIVE);
        userMapper.insert(user);

        if (reqDTO.getRoleIds() != null && !reqDTO.getRoleIds().isEmpty()) {
            List<SysUserRole> userRoles = reqDTO.getRoleIds().stream().map(roleId -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(roleId);
                userRole.setAssignedAt(LocalDateTime.now());
                return userRole;
            }).toList();
            userRoles.forEach(userRoleMapper::insert);
        }

        return user.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRespDTO updateUser(UUID id, UserUpdateReqDTO reqDTO) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        SysUser update = new SysUser();
        update.setId(id);
        boolean updated = false;
        if (StringUtils.hasText(reqDTO.getName())) {
            update.setName(reqDTO.getName());
            updated = true;
        }
        if (StringUtils.hasText(reqDTO.getEmail())) {
            update.setEmail(reqDTO.getEmail());
            updated = true;
        }
        if (updated) {
            userMapper.updateById(update);
        }

        if (reqDTO.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(id);
            if (!reqDTO.getRoleIds().isEmpty()) {
                List<SysUserRole> userRoles = reqDTO.getRoleIds().stream().map(roleId -> {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setUserId(id);
                    userRole.setRoleId(roleId);
                    userRole.setAssignedAt(LocalDateTime.now());
                    return userRole;
                }).toList();
                userRoles.forEach(userRoleMapper::insert);
            }
        }

        return convertToUserRespDTO(userMapper.selectById(id));
    }

    @Override
    public UserRespDTO updateUserStatus(UUID id, String status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        user.setStatus(status);
        return convertToUserRespDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStatus(UserBatchStatusReqDTO reqDTO) {
        for (UUID userId : reqDTO.getUserIds()) {
            SysUser user = userMapper.selectById(userId);
            if (user != null) {
                SysUser update = new SysUser();
                update.setId(userId);
                update.setStatus(reqDTO.getStatus());
                userMapper.updateById(update);
            }
        }
    }

    @Override
    public void resetPassword(UUID id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    @Override
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.OLD_PASSWORD_WRONG);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    @Override
    public SysUser getUserByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public SysUser getUserByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    private UserRespDTO convertToUserRespDTO(SysUser user) {
        UserRespDTO dto = UserConvertMapper.INSTANCE.toRespDTO(user);

        List<SysUserRole> userRoles = userRoleMapper.listByUserId(user.getId());
        if (!userRoles.isEmpty()) {
            List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            List<SysRole> roles = roleMapper.listByIds(roleIds);
            dto.setRoles(roles.stream().map(UserConvertMapper.INSTANCE::toRoleSimple).collect(Collectors.toList()));
        } else {
            dto.setRoles(new ArrayList<>());
        }

        List<WorkspaceUser> workspaceUsers = workspaceUserMapper.listByUserId(user.getId());
        if (!workspaceUsers.isEmpty()) {
            List<UUID> wsIds = workspaceUsers.stream().map(WorkspaceUser::getWorkspaceId).collect(Collectors.toList());
            List<Workspace> workspaces = workspaceMapper.listByIds(wsIds);
            dto.setWorkspaces(workspaces.stream().map(ws -> {
                WorkspaceUser matchedWs = workspaceUsers.stream()
                        .filter(wu -> wu.getWorkspaceId().equals(ws.getId()))
                        .findFirst().orElse(null);
                return UserConvertMapper.INSTANCE.toWorkspaceSimple(ws, matchedWs);
            }).collect(Collectors.toList()));
        } else {
            dto.setWorkspaces(new ArrayList<>());
        }

        return dto;
    }
}
