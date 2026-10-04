package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEmbeddingGateTest {

    @Mock
    private AiEmbeddingConfigMapper embeddingMapper;

    @InjectMocks
    private AiEmbeddingGate gate;

    private static AiEmbeddingConfig config(boolean enabled, List<Map<String, Object>> versions) {
        AiEmbeddingConfig row = new AiEmbeddingConfig();
        row.setEnabled(enabled);
        row.setVersions(versions);
        return row;
    }

    @Test
    void requireEnabledThrowsWhenAbsentOrDisabled() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);
        ServiceException absent = assertThrows(ServiceException.class, gate::requireEnabled);
        assertEquals(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code(), absent.getCode());

        when(embeddingMapper.selectSingleton()).thenReturn(config(false, List.of()));
        ServiceException disabled = assertThrows(ServiceException.class, gate::requireEnabled);
        assertEquals(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code(), disabled.getCode());
    }

    @Test
    void requireReadyBlocksDuringReindex() {
        AiEmbeddingConfig pending = config(true, List.of(Map.of("version", 1)));
        when(embeddingMapper.selectSingleton()).thenReturn(pending);

        ServiceException ex = assertThrows(ServiceException.class, gate::requireReady);
        assertEquals(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE.code(), ex.getCode());
    }

    @Test
    void requireReadyPassesWhenVersionsConsumed() {
        AiEmbeddingConfig ready = config(true, new ArrayList<>());
        when(embeddingMapper.selectSingleton()).thenReturn(ready);

        assertSame(ready, gate.requireReady());
    }

    @Test
    void writableConfigSkipsWhenUnready() {
        when(embeddingMapper.selectSingleton()).thenReturn(null);
        assertNull(gate.writableConfig());

        when(embeddingMapper.selectSingleton()).thenReturn(config(true, List.of(Map.of("version", 1))));
        assertNull(gate.writableConfig());
    }

    @Test
    void writableConfigReturnsRowWhenReady() {
        AiEmbeddingConfig ready = config(true, List.of());
        when(embeddingMapper.selectSingleton()).thenReturn(ready);

        assertSame(ready, gate.writableConfig());
    }
}
