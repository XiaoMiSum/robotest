package io.github.xiaomisum.robotest.service.admin.audit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface LoginAuditService {

    /**
     * 登录成功后写入审计记录（operation=LOGIN，含登录 IP）。
     * 写入失败不影响登录（由 AuditLogWriter 兜底）。
     */
    void recordLogin(UUID userId, String username, HttpServletRequest request);
}
