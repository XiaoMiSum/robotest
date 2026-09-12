package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.model.dto.request.admin.InitSetupReqDTO;
import io.github.xiaomisum.robotest.service.admin.SysInitService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/auth/init")
public class SysInitController {

    @Resource
    private SysInitService sysInitService;

    /**
     * 检查系统是否已初始化（是否有 admin 账号）
     */
    @GetMapping("/status")
    public Result<InitStatusRespVO> getStatus() {
        return Result.ok(new InitStatusRespVO(sysInitService.isInitialized()));
    }

    /**
     * 初始化系统：创建 admin 账号（仅首次安装时调用）
     */
    @PostMapping("/setup")
    public Result<Void> setup(@RequestBody @Valid InitSetupReqDTO reqDTO) {
        sysInitService.setup(reqDTO);
        return Result.ok();
    }

    /**
     * 初始化状态响应
     */
    public record InitStatusRespVO(boolean initialized) {
    }
}