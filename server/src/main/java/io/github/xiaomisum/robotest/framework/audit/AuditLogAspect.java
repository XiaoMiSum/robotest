package io.github.xiaomisum.robotest.framework.audit;

import tools.jackson.databind.node.ObjectNode;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.migoo.framework.common.util.JsonUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    private final AuditLogWriter auditLogWriter;
    private final ClientIpResolver clientIpResolver;

    public AuditLogAspect(AuditLogWriter auditLogWriter, ClientIpResolver clientIpResolver) {
        this.auditLogWriter = auditLogWriter;
        this.clientIpResolver = clientIpResolver;
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
                record.setRequestIp(clientIpResolver.resolve(request));
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
                ObjectNode changesNode = JsonUtils.createObjectNode();
                Parameter[] parameters = method.getParameters();
                Object[] args = joinPoint.getArgs();
                for (int i = 0; i < parameters.length; i++) {
                    String name = parameters[i].getName();
                    if (SENSITIVE_FIELDS.stream().anyMatch(f -> name.toLowerCase().contains(f))) {
                        continue;
                    }
                    if (args[i] != null) {
                        changesNode.set(name, JsonUtils.valueToTree(args[i]));
                    }
                }
                record.setChanges(JsonUtils.convert(changesNode, Map.class));
            } else {
                record.setChanges(Map.of());
            }

            auditLogWriter.write(record);
        } catch (Exception e) {
            log.warn("[AuditLog] Failed to write audit log: {}", e.getMessage());
        }

        return result;
    }
}
