package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.common.util.PasswordValidator;
import io.github.xiaomisum.robotest.convert.UserConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.UserBatchStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.UserUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.UserRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.Workspace;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

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
    public PageResult<UserRespDTO> getUserPage(String keyword, String status, String roleId, String workspaceId,
                                                Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getEmail, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
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
    public UserRespDTO getUserDetail(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        return convertToUserRespDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createUser(UserCreateReqDTO reqDTO) {
        // 校验密码强度
        PasswordValidator.validate(reqDTO.getPassword());
        // 校验用户名唯一
        if (userMapper.selectOne(SysUser::getUsername, reqDTO.getUsername()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USERNAME_EXISTS);
        }
        // 校验邮箱唯一
        if (userMapper.selectOne(SysUser::getEmail, reqDTO.getEmail()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.EMAIL_EXISTS);
        }
        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(reqDTO.getUsername());
        user.setEmail(reqDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(reqDTO.getPassword()));
        user.setStatus("active");
        userMapper.insert(user);

        // 分配角色
        if (!CollectionUtils.isEmpty(reqDTO.getRoleIds())) {
            List<SysUserRole> userRoles = reqDTO.getRoleIds().stream().map(roleId -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(user.getId().toString());
                userRole.setRoleId(roleId.toString());
                userRole.setAssignedAt(LocalDateTime.now());
                return userRole;
            }).collect(Collectors.toList());
            userRoles.forEach(userRoleMapper::insert);
        }

        // 分配工作空间
        if (!CollectionUtils.isEmpty(reqDTO.getWorkspaceIds())) {
            List<WorkspaceUser> workspaceUsers = reqDTO.getWorkspaceIds().stream().map(wsId -> {
                WorkspaceUser wu = new WorkspaceUser();
                wu.setUserId(user.getId().toString());
                wu.setWorkspaceId(wsId.toString());
                wu.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID);
                wu.setJoinedAt(LocalDateTime.now());
                return wu;
            }).collect(Collectors.toList());
            workspaceUsers.forEach(workspaceUserMapper::insert);
        }

        return user.getId().toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRespDTO updateUser(String id, UserUpdateReqDTO reqDTO) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        // 更新邮箱
        if (StringUtils.hasText(reqDTO.getEmail())) {
            user.setEmail(reqDTO.getEmail());
            userMapper.updateById(user);
        }

        // 全量替换角色
        if (reqDTO.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            if (!reqDTO.getRoleIds().isEmpty()) {
                List<SysUserRole> userRoles = reqDTO.getRoleIds().stream().map(roleId -> {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setUserId(id);
                    userRole.setRoleId(roleId.toString());
                    userRole.setAssignedAt(LocalDateTime.now());
                    return userRole;
                }).collect(Collectors.toList());
                userRoles.forEach(userRoleMapper::insert);
            }
        }

        // 全量替换工作空间
        if (reqDTO.getWorkspaceIds() != null) {
            workspaceUserMapper.delete(new LambdaQueryWrapper<WorkspaceUser>().eq(WorkspaceUser::getUserId, id));
            if (!reqDTO.getWorkspaceIds().isEmpty()) {
                List<WorkspaceUser> workspaceUsers = reqDTO.getWorkspaceIds().stream().map(wsId -> {
                    WorkspaceUser wu = new WorkspaceUser();
                    wu.setUserId(id);
                    wu.setWorkspaceId(wsId.toString());
                    wu.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID);
                    wu.setJoinedAt(LocalDateTime.now());
                    return wu;
                }).collect(Collectors.toList());
                workspaceUsers.forEach(workspaceUserMapper::insert);
            }
        }

        return convertToUserRespDTO(userMapper.selectById(id));
    }

    @Override
    public UserRespDTO updateUserStatus(String id, String status) {
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
    public void resetPassword(String id, String newPassword) {
        PasswordValidator.validate(newPassword);
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

        List<SysUserRole> userRoles = userRoleMapper.selectList(SysUserRole::getUserId, user.getId());
        if (!userRoles.isEmpty()) {
            List<String> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            List<SysRole> roles = roleMapper.selectList(SysRole::getId, roleIds);
            dto.setRoles(roles.stream().map(UserConvertMapper.INSTANCE::toRoleSimple).collect(Collectors.toList()));
        } else {
            dto.setRoles(new ArrayList<>());
        }

        List<WorkspaceUser> workspaceUsers = workspaceUserMapper.selectList(WorkspaceUser::getUserId, user.getId());
        if (!workspaceUsers.isEmpty()) {
            List<String> wsIds = workspaceUsers.stream().map(WorkspaceUser::getWorkspaceId).collect(Collectors.toList());
            List<Workspace> workspaces = workspaceMapper.selectList(Workspace::getId, wsIds);
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
