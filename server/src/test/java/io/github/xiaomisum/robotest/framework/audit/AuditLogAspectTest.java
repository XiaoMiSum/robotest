package io.github.xiaomisum.robotest.framework.audit;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;
    @Captor
    private ArgumentCaptor<AuditRecordedEvent> eventCaptor;

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        // mock 事务管理器：返回假 transaction status，避免真实事务基础设施启动
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        aspect = new AuditLogAspect(auditLogMapper, transactionManager, eventPublisher);

        LoginUser loginUser = new LoginUser();
        loginUser.setId(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private final UUID probeEntityId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void around_recordsEntityIdFromFirstUuidParam() throws Throwable {
        var jp = joinPoint(new Object[]{probeEntityId, "pw", 1});
        when(jp.proceed()).thenReturn("ok");

        Object result = aspect.around(jp);

        assertEquals("ok", result);
        verify(auditLogMapper).insert(auditLogCaptor.capture());
        AuditLog record = auditLogCaptor.getValue();
        assertEquals(probeEntityId, record.getEntityId());
        assertEquals("update", record.getOperation());
        assertEquals("User", record.getEntityType());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000007"), record.getOperatorId());
        // 写库成功后发布消费事件
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        AuditRecordedEvent event = eventCaptor.getValue();
        assertEquals(record.getId(), event.auditLogId());
        assertEquals("User", event.entityType());
    }

    @Test
    void around_masksSensitiveFieldsInChanges() throws Throwable {
        var jp = joinPoint(new Object[]{probeEntityId, "SecretPass!1", 25});
        when(jp.proceed()).thenReturn("ok");

        aspect.around(jp);

        verify(auditLogMapper).insert(auditLogCaptor.capture());
        Map<String, Object> changes = auditLogCaptor.getValue().getChanges();
        assertNotNull(changes);
        assertTrue(changes.containsKey("id"));
        assertFalse(changes.keySet().stream().anyMatch(k -> k.toLowerCase().contains("password")));
        // password 参数被脱敏剔除，只保留 id 与 age
        assertEquals(2, changes.size());
    }

    @Test
    void around_auditFailureDoesNotAffectBusinessResult() throws Throwable {
        var jp = joinPoint(new Object[]{probeEntityId, "pw", 1});
        when(jp.proceed()).thenReturn("business-ok");
        // 审计写入失败：不应向调用方传播异常
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any(AuditLog.class));

        Object result = aspect.around(jp);

        assertEquals("business-ok", result);
        verify(auditLogMapper).insert(any(AuditLog.class));
    }

    @Test
    void around_auditFailureDoesNotPublishEvent() throws Throwable {
        var jp = joinPoint(new Object[]{probeEntityId, "pw", 1});
        when(jp.proceed()).thenReturn("business-ok");
        // 审计写入失败：业务结果不受影响，且不得发布消费事件（避免消费不存在的记录）
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any(AuditLog.class));

        Object result = aspect.around(jp);

        assertEquals("business-ok", result);
        verify(auditLogMapper).insert(any(AuditLog.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void around_logParamsFalse_recordsEmptyChanges() throws Throwable {
        ProceedingJoinPoint jp = simpleJoinPoint();
        Method rejectedMethod = AuditLogAspectTest.class.getDeclaredMethod("probeNoLog", String.class);
        MethodSignature rejectedSig = methodSignature(rejectedMethod);
        doReturn(rejectedSig).when(jp).getSignature();
        when(jp.proceed()).thenReturn("ok");

        aspect.around(jp);

        verify(auditLogMapper).insert(auditLogCaptor.capture());
        assertEquals(Map.of(), auditLogCaptor.getValue().getChanges());
    }

    @Test
    void around_withoutAuth_returnsBusinessResultWithoutOperator() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(null);
        var jp = joinPoint(new Object[]{probeEntityId, "pw", 1});
        when(jp.proceed()).thenReturn("ok");

        Object result = aspect.around(jp);

        assertEquals("ok", result);
        verify(auditLogMapper).insert(auditLogCaptor.capture());
        assertNull(auditLogCaptor.getValue().getOperatorId());
    }

    private ProceedingJoinPoint joinPoint(Object... args) throws NoSuchMethodException {
        ProceedingJoinPoint jp = simpleJoinPoint();
        Method method = AuditLogAspectTest.class.getDeclaredMethod("probe",
                UUID.class, String.class, Integer.class);
        MethodSignature signature = methodSignature(method);
        doReturn(signature).when(jp).getSignature();
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    private static MethodSignature methodSignature(Method method) {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        return signature;
    }

    private static ProceedingJoinPoint simpleJoinPoint() {
        return mock(ProceedingJoinPoint.class);
    }

    // ===== probe methods（复用真实方法元数据，触发 @AuditOperation 扫描路径） =====

    @AuditOperation(operation = "update", entityType = "User")
    private void probe(UUID id, String password, Integer age) {
    }

    @AuditOperation(operation = "delete", entityType = "Role", logParams = false)
    private void probeNoLog(String name) {
    }
}