package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.admin.InitSetupReqDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/init")
public class SysInitController {

    private static final UUID ADMIN_ROLE_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private PasswordEncoder passwordEncoder;

    /**
     * 检查系统是否已初始化（是否有 admin 账号）
     */
    @GetMapping("/status")
    public Result<InitStatusRespVO> getStatus() {
        long count = sysUserMapper.selectCount(null);
        return Result.ok(new InitStatusRespVO(count > 0));
    }

    /**
     * 初始化系统：创建 admin 账号（仅首次安装时调用）
     */
    @PostMapping("/setup")
    public Result<Void> setup(@RequestBody @Valid InitSetupReqDTO reqDTO) {
        // 重复初始化校验
        long count = sysUserMapper.selectCount(null);
        if (count > 0) {
            return Result.error(ErrorCodeConstants.SYSTEM_ALREADY_INITIALIZED);
        }

        // 创建 admin 用户
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setName("系统管理员");
        admin.setEmail("admin@robotest.local");
        admin.setPasswordHash(passwordEncoder.encode(reqDTO.getPassword()));
        admin.setStatus("active");
        sysUserMapper.insert(admin);

        // 分配系统管理员角色
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(ADMIN_ROLE_ID);
        sysUserRoleMapper.insert(userRole);

        return Result.ok();
    }

    /**
     * 初始化状态响应
     */
    public record InitStatusRespVO(boolean initialized) {
    }
}
