package io.github.xiaomisum.robotest.framework.audit;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate auditTxTemplate;

    public AuditLogAspect(AuditLogMapper auditLogMapper, ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
        this.auditTxTemplate = new TransactionTemplate(transactionManager);
        // 审计写入独立事务：失败只丢弃审计记录本身，不随业务事务静默回滚
        this.auditTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwordHash", "token", "accessToken", "refreshToken", "secret"
    );

    @Around("@annotation(io.github.xiaomisum.robotest.framework.audit.AuditOperation)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditOperation annotation = method.getAnnotation(AuditOperation.class);

            AuditLog record = new AuditLog();
            record.setOperation(annotation.operation());
            record.setEntityType(annotation.entityType());

            // operator
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof io.github.xiaomisum.robotest.framework.security.LoginUser loginUser) {
                record.setOperatorId(loginUser.getId());
                record.setOperatorName(loginUser.getUsername());
            }

            // IP
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                record.setRequestIp(getClientIp(request));
            }

            // entityId：取第一个 UUID 参数
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                if (params[i].getType() == UUID.class) {
                    Object val = joinPoint.getArgs()[i];
                    if (val instanceof UUID uuid) {
                        record.setEntityId(uuid);
                        break;
                    }
                }
            }

            // changes
            if (annotation.logParams()) {
                ObjectNode changesNode = objectMapper.createObjectNode();
                Parameter[] parameters = method.getParameters();
                Object[] args = joinPoint.getArgs();
                for (int i = 0; i < parameters.length; i++) {
                    String name = parameters[i].getName();
                    if (SENSITIVE_FIELDS.stream().anyMatch(f -> name.toLowerCase().contains(f))) {
                        continue;
                    }
                    if (args[i] != null) {
                        changesNode.set(name, objectMapper.valueToTree(args[i]));
                    }
                }
                record.setChanges(objectMapper.convertValue(changesNode, Map.class));
            } else {
                record.setChanges(Map.of());
            }

            auditTxTemplate.executeWithoutResult(status -> auditLogMapper.insert(record));
        } catch (Exception e) {
            log.warn("[AuditLog] Failed to write audit log: {}", e.getMessage());
        }

        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "";
    }
}
