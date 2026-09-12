package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.dto.request.admin.InitSetupReqDTO;

/**
 * 系统初始化：检查/创建 admin 账号。
 */
public interface SysInitService {

    boolean isInitialized();

    void setup(InitSetupReqDTO reqDTO);
}