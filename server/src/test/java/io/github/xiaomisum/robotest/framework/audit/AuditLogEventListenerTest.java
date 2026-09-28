package io.github.xiaomisum.robotest.framework.audit;

import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import xyz.migoo.framework.common.observability.AuditLogEvent;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 框架 {@link AuditLogEvent} → {@code sys_audit_log} 的组装口径（审计查询详细设计 4.3）。
 */
@ExtendWith(MockitoExtension.class)
class AuditLogEventListenerTest {

    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<AuditLog> recordCaptor;
    @Captor
    private ArgumentCaptor<AuditRecordedEvent> eventCaptor;

    private AuditLogEventListener listener;

    @BeforeEach
    void setUp() {
        // 仅落库用例触发事务，保持 lenient 以避免未落库用例的多余桩告警
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        listener = new AuditLogEventListener(
                new AuditLogWriter(auditLogMapper, transactionManager, eventPublisher));
    }

    private AuditLogEvent event(String action, boolean success, String params) {
        return new AuditLogEvent(OPERATOR_ID + "(admin)", "10.0.0.1",
                "/api/admin/users/{id}", action, success, null, params);
    }

    @Test
    void successEvent_writesRecordAndPublishesRecordedEvent() {
        listener.onAuditLogEvent(event("CREATE:ApiEnvironment", true,
                "[\"" + ENTITY_ID + "\",\"dev\"]"));

        verify(auditLogMapper).insert(recordCaptor.capture());
        AuditLog record = recordCaptor.getValue();
        assertEquals("CREATE", record.getOperation());
        assertEquals("ApiEnvironment", record.getEntityType());
        assertEquals(OPERATOR_ID, record.getOperatorId());
        assertEquals("admin", record.getOperatorName());
        assertEquals(ENTITY_ID, record.getEntityId());
        assertEquals("10.0.0.1", record.getRequestIp());
        assertTrue(record.getChanges().containsKey("params"));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("ApiEnvironment", eventCaptor.getValue().entityType());
        assertEquals("CREATE", eventCaptor.getValue().operation());
    }

    @Test
    void failedEvent_isNotPersisted() {
        listener.onAuditLogEvent(event("DELETE:AiChatModel", false, "[\"" + ENTITY_ID + "\"]"));

        verifyNoInteractions(auditLogMapper, eventPublisher);
    }

    @Test
    void anonymousEvent_isIgnored() {
        AuditLogEvent anonymous = new AuditLogEvent("anonymous", "10.0.0.1",
                "/api/x", "UPDATE:User", true, null, null);
        listener.onAuditLogEvent(anonymous);

        verifyNoInteractions(auditLogMapper, eventPublisher);
    }

    @Test
    void actionWithoutSeparator_isIgnored() {
        listener.onAuditLogEvent(event("UserServiceImpl#update", true, null));

        verifyNoInteractions(auditLogMapper, eventPublisher);
    }

    @Test
    void blankParams_storesEmptyChangesAndNullEntityId() {
        listener.onAuditLogEvent(event("UPDATE:AiConfig", true, null));

        verify(auditLogMapper).insert(recordCaptor.capture());
        AuditLog record = recordCaptor.getValue();
        assertEquals(Map.of(), record.getChanges());
        assertNull(record.getEntityId());
    }

    @Test
    void nonUuidParams_leaveEntityIdNull() {
        listener.onAuditLogEvent(event("UPDATE:ApiEnvironment", true, "[\"not-a-uuid\"]"));

        verify(auditLogMapper).insert(recordCaptor.capture());
        assertNull(recordCaptor.getValue().getEntityId());
    }
}
