package io.github.xiaomisum.robotest.service.admin.audit;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAuditServiceImplTest {

    @Mock
    private AuditLogWriter auditLogWriter;
    @Spy
    private ClientIpResolver clientIpResolver = new ClientIpResolver();

    @InjectMocks
    private LoginAuditServiceImpl service;

    @Captor
    private ArgumentCaptor<AuditLog> recordCaptor;

    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000007");

    @Test
    void recordLogin_buildsLoginRecordWithUserIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");

        service.recordLogin(userId, "admin", request);

        verify(auditLogWriter).write(recordCaptor.capture());
        AuditLog record = recordCaptor.getValue();
        assertEquals("LOGIN", record.getOperation());
        assertEquals("User", record.getEntityType());
        assertEquals(userId, record.getEntityId());
        assertEquals(userId, record.getOperatorId());
        assertEquals("admin", record.getOperatorName());
        assertEquals("10.0.0.8", record.getRequestIp());
        assertEquals(Map.of(), record.getChanges());
    }

    @Test
    void recordLogin_takesFirstForwardedForHopAsClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        service.recordLogin(userId, "admin", request);

        verify(auditLogWriter).write(recordCaptor.capture());
        // 反向代理链首段才是真实客户端
        assertEquals("203.0.113.7", recordCaptor.getValue().getRequestIp());
    }

    @Test
    void recordLogin_resolvesIpEvenWithoutRequest() {
        // 极端场景（无 request）不抛异常，IP 记为空串
        assertDoesNotThrow(() -> service.recordLogin(userId, "admin", null));

        verify(auditLogWriter).write(recordCaptor.capture());
        assertEquals("", recordCaptor.getValue().getRequestIp());
    }
}
