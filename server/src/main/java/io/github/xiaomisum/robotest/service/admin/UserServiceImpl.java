package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.UserConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.UserRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.UserSimpleRespDTO;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

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
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getEmail, keyword)
                    .or().like(SysUser::getName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (roleId != null) {
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
            List<UUID> userIds = userRoles.stream().map(SysUserRole::getUserId).collect(Collectors.toList());
            if (userIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L);
            }
            wrapper.in(SysUser::getId, userIds);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        PageResult<SysUser> userPage = userMapper.selectPage(new xyz.migoo.framework.common.pojo.PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, wrapper);

        List<UserRespDTO> records = userPage.getList().stream()
                .map(this::convertToUserRespDTO)
                .collect(Collectors.toList());

        return new PageResult<>(records, userPage.getTotal());
    }

    @Override
    public List<UserSimpleRespDTO> getUserSimpleList(String keyword) {
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<SysUser>()
                .eq(SysUser::getStatus, Constants.Status.ACTIVE);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getName, keyword)
                    .or().like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getEmail, keyword);
        }
        wrapper.orderByAsc(SysUser::getName);

        List<SysUser> users = userMapper.selectList(wrapper);
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
        // 鏍￠獙鐢ㄦ埛鍚嶅敮涓€
        if (userMapper.selectOne(SysUser::getUsername, reqDTO.getUsername()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USERNAME_EXISTS);
        }
        // 鏍￠獙閭鍞竴
        if (userMapper.selectOne(SysUser::getEmail, reqDTO.getEmail()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.EMAIL_EXISTS);
        }
        // 鍒涘缓鐢ㄦ埛
        SysUser user = new SysUser();
        user.setUsername(reqDTO.getUsername());
        user.setName(reqDTO.getName());
        user.setEmail(reqDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(reqDTO.getPassword()));
        user.setStatus(Constants.Status.ACTIVE);
        userMapper.insert(user);

        // 鍒嗛厤瑙掕壊
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

        boolean updated = false;
        // 鏇存柊濮撳悕
        if (StringUtils.hasText(reqDTO.getName())) {
            user.setName(reqDTO.getName());
            updated = true;
        }
        // 鏇存柊閭
        if (StringUtils.hasText(reqDTO.getEmail())) {
            user.setEmail(reqDTO.getEmail());
            updated = true;
        }
        if (updated) {
            userMapper.updateById(user);
        }

        // 鍏ㄩ噺鏇挎崲瑙掕壊
        if (reqDTO.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, id));
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
        user.setStatus(status);
        userMapper.updateById(user);
        return convertToUserRespDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStatus(UserBatchStatusReqDTO reqDTO) {
        for (UUID userId : reqDTO.getUserIds()) {
            SysUser user = userMapper.selectById(userId);
            if (user != null) {
                user.setStatus(reqDTO.getStatus());
                userMapper.updateById(user);
            }
        }
    }

    @Override
    public void resetPassword(UUID id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    public SysUser getUserByUsername(String username) {
        return userMapper.selectOne(SysUser::getUsername, username);
    }

    @Override
    public SysUser getUserByEmail(String email) {
        return userMapper.selectOne(SysUser::getEmail, email);
    }

    private UserRespDTO convertToUserRespDTO(SysUser user) {
        UserRespDTO dto = UserConvertMapper.INSTANCE.toRespDTO(user);

        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
        if (!userRoles.isEmpty()) {
            List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            List<SysRole> roles = roleMapper.selectList(
                    new LambdaQueryWrapperX<SysRole>().in(SysRole::getId, roleIds));
            dto.setRoles(roles.stream().map(UserConvertMapper.INSTANCE::toRoleSimple).collect(Collectors.toList()));
        } else {
            dto.setRoles(new ArrayList<>());
        }

        List<WorkspaceUser> workspaceUsers = workspaceUserMapper.selectList(
                new LambdaQueryWrapperX<WorkspaceUser>().eq(WorkspaceUser::getUserId, user.getId()));
        if (!workspaceUsers.isEmpty()) {
            List<UUID> wsIds = workspaceUsers.stream().map(WorkspaceUser::getWorkspaceId).collect(Collectors.toList());
            List<Workspace> workspaces = workspaceMapper.selectList(
                    new LambdaQueryWrapperX<Workspace>().in(Workspace::getId, wsIds));
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
