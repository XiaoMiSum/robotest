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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogWriterTest {

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

    private AuditLogWriter writer;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        writer = new AuditLogWriter(auditLogMapper, transactionManager, eventPublisher);
    }

    private AuditLog record() {
        AuditLog log = new AuditLog();
        log.setOperation("LOGIN");
        log.setEntityType("User");
        log.setEntityId(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        log.setOperatorId(UUID.fromString("00000000-0000-0000-0000-000000000007"));
        log.setOperatorName("admin");
        log.setChanges(Map.of());
        return log;
    }

    @Test
    void write_insertsRecordAndPublishesEventAfterSuccess() {
        AuditLog record = record();
        when(auditLogMapper.insert(record)).thenAnswer(invocation -> {
            record.setId(UUID.fromString("00000000-0000-0000-0000-000000000009"));
            return 1;
        });

        writer.write(record);

        verify(auditLogMapper).insert(recordCaptor.capture());
        AuditLog saved = recordCaptor.getValue();
        assertEquals("LOGIN", saved.getOperation());
        assertEquals("User", saved.getEntityType());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000007"), saved.getOperatorId());
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("LOGIN", eventCaptor.getValue().operation());
        assertEquals("User", eventCaptor.getValue().entityType());
    }

    @Test
    void write_insertFailureIsSwallowedAndEventNotPublished() {
        when(auditLogMapper.insert(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> writer.write(record()));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
