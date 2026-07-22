package io.github.xiaomisum.robotest.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.type.TypeReference;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.RoleConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RolePermissionsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.RoleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.PermissionTableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.RoleUserRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysPermission;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import io.github.xiaomisum.robotest.repository.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import io.github.xiaomisum.robotest.repository.SysUserRoleMapper;
import io.github.xiaomisum.robotest.service.admin.RoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.json.JsonUtils;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysPermissionMapper permissionMapper;

    @Override
    public List<RoleTreeRespDTO> getRoleTree() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getType, Constants.RoleType.SYSTEM));

        RoleTreeRespDTO groupNode = new RoleTreeRespDTO();
        groupNode.setId(UUID.nameUUIDFromBytes("type-system".getBytes()));
        groupNode.setName("绯荤粺瑙掕壊");
        groupNode.setType(Constants.RoleType.SYSTEM);
        groupNode.setIsGroup(true);
        groupNode.setChildren(roles.stream().map(role -> {
            RoleTreeRespDTO node = new RoleTreeRespDTO();
            node.setId(role.getId());
            node.setName(role.getName());
            node.setType(role.getType());
            node.setIsSystem(role.getIsSystem());
            node.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
            return node;
        }).collect(Collectors.toList()));

        List<RoleTreeRespDTO> result = new ArrayList<>();
        result.add(groupNode);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRole(RoleCreateReqDTO reqDTO) {
        // 鏍￠獙鍚嶇О鍞竴
        if (roleMapper.selectOne(SysRole::getName, reqDTO.getName()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NAME_EXISTS);
        }
        // 鏍￠獙绫诲瀷
        if (!Constants.RoleType.SYSTEM.equals(reqDTO.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_TYPE_ERROR);
        }

        SysRole role = new SysRole();
        role.setName(reqDTO.getName());
        role.setType(reqDTO.getType());
        role.setIsSystem(false);
        role.setPermissions(List.of());
        roleMapper.insert(role);
        return role.getId().toString();
    }

    @Override
    public RoleRespDTO updateRole(String id, RoleUpdateReqDTO reqDTO) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        // 鏍￠獙鍚嶇О鍞竴锛堟帓闄よ嚜韬級
        SysRole existing = roleMapper.selectOne(SysRole::getName, reqDTO.getName());
        if (existing != null && !existing.getId().equals(id)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NAME_EXISTS);
        }
        role.setName(reqDTO.getName());
        roleMapper.updateById(role);
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public void deleteRole(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SYSTEM_ROLE_NOT_DELETABLE);
        }
        // 妫€鏌ユ槸鍚︽湁鐢ㄦ埛寮曠敤
        Long userCount = userRoleMapper.selectCount(SysUserRole::getRoleId, id);
        if (userCount > 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_IN_USE);
        }
        roleMapper.deleteById(id);
    }

    @Override
    public RoleRespDTO getRoleDetail(String id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public PageResult<RoleUserRespDTO> getRoleUsers(String id, Integer pageNo, Integer pageSize) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }

        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id);

        PageResult<SysUserRole> userRolePage = userRoleMapper.selectPage(
                new xyz.migoo.framework.common.pojo.PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, wrapper);

        List<RoleUserRespDTO> records = userRolePage.getList().stream().map(ur -> {
            SysUser user = userMapper.selectById(ur.getUserId());
            if (user == null) return null;
            RoleUserRespDTO dto = new RoleUserRespDTO();
            dto.setId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            dto.setStatus(user.getStatus());
            dto.setCreatedAt(user.getCreatedAt());
            return dto;
        }).filter(dto -> dto != null).collect(Collectors.toList());

        return new PageResult<>(records, userRolePage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRoleUsers(String id, List<String> userIds) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        for (String userId : userIds) {
            // 璺宠繃宸插瓨鍦ㄧ殑
            Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId)
                    .eq(SysUserRole::getRoleId, id));
            if (count > 0) continue;

            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(id);
            userRole.setAssignedAt(java.time.LocalDateTime.now());
            userRoleMapper.insert(userRole);
        }
    }

    @Override
    public void removeRoleUser(String id, String userId) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, id));
    }

    @Override
    public RoleRespDTO updateRolePermissions(String id, RolePermissionsUpdateReqDTO reqDTO) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ROLE_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            // 绯荤粺棰勭疆瑙掕壊鐨勫凡鏈夋潈闄愪笉鍙Щ闄?
            List<String> currentPerms = role.getPermissions() != null ? role.getPermissions() : List.of();
            List<String> newPerms = reqDTO.getPermissions();
            // 妫€鏌ユ柊鏉冮檺鍒楄〃鏄惁鍖呭惈鎵€鏈夊凡鏈夋潈闄?
            if (!currentPerms.containsAll(newPerms)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.SYSTEM_ROLE_PERMISSION_NOT_MODIFIABLE);
            }
        }
        role.setPermissions(reqDTO.getPermissions());
        roleMapper.updateById(role);
        RoleRespDTO dto = RoleConvertMapper.INSTANCE.toRespDTO(role);
        dto.setUserCount(Math.toIntExact(userRoleMapper.selectCount(SysUserRole::getRoleId, role.getId())));
        return dto;
    }

    @Override
    public List<PermissionTableRespDTO> getPermissionTable() {
        List<SysPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getModule, SysPermission::getSortOrder));

        // 鎸夋ā鍧楀垎缁?
        return permissions.stream()
                .filter(p -> p.getParentCode() != null) // 鍙繑鍥炲彾瀛愭潈闄愮偣
                .collect(Collectors.groupingBy(SysPermission::getModule))
                .entrySet().stream()
                .map(entry -> {
                    PermissionTableRespDTO dto = new PermissionTableRespDTO();
                    dto.setModule(entry.getKey());
                    dto.setPermissions(entry.getValue().stream().map(p -> {
                        PermissionTableRespDTO.PermissionItem item = new PermissionTableRespDTO.PermissionItem();
                        item.setCode(p.getCode());
                        item.setName(p.getName());
                        return item;
                    }).collect(Collectors.toList()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserPermissionCodes(String userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(SysUserRole::getUserId, userId);
        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.selectList(SysRole::getId, roleIds);

        return roles.stream()
                .flatMap(role -> {
                    List<String> perms = role.getPermissions() != null ? role.getPermissions() : List.of();
                    return perms != null ? perms.stream() : new ArrayList<String>().stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
