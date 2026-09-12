package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.admin.InitSetupReqDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

@Service
public class SysInitServiceImpl implements SysInitService {

    private static final UUID ADMIN_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public boolean isInitialized() {
        return userMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setup(InitSetupReqDTO reqDTO) {
        if (isInitialized()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SYSTEM_ALREADY_INITIALIZED);
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setName("系统管理员");
        admin.setEmail("admin@robotest.local");
        admin.setPasswordHash(passwordEncoder.encode(reqDTO.getPassword()));
        admin.setStatus(Constants.Status.ACTIVE);
        userMapper.insert(admin);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(ADMIN_ROLE_ID);
        userRoleMapper.insert(userRole);
    }
}