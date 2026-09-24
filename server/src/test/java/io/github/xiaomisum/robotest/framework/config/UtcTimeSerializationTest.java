package io.github.xiaomisum.robotest.framework.config;

import tools.jackson.databind.json.JsonMapper;
import io.github.xiaomisum.robotest.model.dto.request.workspace.InvitationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.InvitationRespDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtcTimeSerializationTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new UtcTimeSerializationConfig().utcTimeModule())
            .build();

    @Test
    void serializesEventTimeWithZ() throws Exception {
        EventTimeDto dto = new EventTimeDto(LocalDateTime.of(2026, 9, 24, 12, 34, 56));

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("2026-09-24T12:34:56Z"));
    }

    @Test
    void serializesInvitationExpirationWithZ() throws Exception {
        InvitationRespDTO dto = new InvitationRespDTO();
        dto.setExpiresAt(LocalDateTime.of(2026, 12, 31, 23, 59, 59));

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"expiresAt\":\"2026-12-31T23:59:59Z\""), json);
    }

    @Test
    void acceptsLocalDateTimeInvitationRequest() throws Exception {
        InvitationCreateReqDTO dto = objectMapper.readValue(
                "{\"expiresAt\":\"2026-12-31T23:59:59\"}",
                InvitationCreateReqDTO.class);

        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59, 59), dto.getExpiresAt());
    }

    @Test
    void formatsUtcTimeFromUtility() {
        assertEquals("2026-09-24T12:34:56Z",
                io.github.xiaomisum.robotest.framework.time.UtcTime.toIso(
                        LocalDateTime.of(2026, 9, 24, 12, 34, 56)));
    }

    static class EventTimeDto {
        public LocalDateTime occurredAt;

        EventTimeDto(LocalDateTime occurredAt) {
            this.occurredAt = occurredAt;
        }
    }
}
