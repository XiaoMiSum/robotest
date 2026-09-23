package io.github.xiaomisum.robotest.service.admin.audit;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class LoginAuditServiceImpl implements LoginAuditService {

    /** operation 取值与注解路径统一由审计查询侧消费（见审计查询详细设计 §2） */
    static final String OPERATION_LOGIN = "LOGIN";
    static final String ENTITY_TYPE_USER = "User";

    @Resource
    private AuditLogWriter auditLogWriter;
    @Resource
    private ClientIpResolver clientIpResolver;

    @Override
    public void recordLogin(UUID userId, String username, HttpServletRequest request) {
        AuditLog record = new AuditLog();
        record.setOperation(OPERATION_LOGIN);
        record.setEntityType(ENTITY_TYPE_USER);
        record.setEntityId(userId);
        record.setOperatorId(userId);
        record.setOperatorName(username);
        record.setRequestIp(clientIpResolver.resolve(request));
        record.setChanges(Map.of());
        auditLogWriter.write(record);
    }
}
