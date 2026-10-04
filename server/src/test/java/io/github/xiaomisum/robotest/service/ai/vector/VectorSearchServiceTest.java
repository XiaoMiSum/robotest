package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.repository.ai.AiVectorIndexMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private AiEmbeddingGate gate;
    @Mock
    private AiEmbeddingClient embeddingClient;
    @Mock
    private AiVectorIndexMapper indexMapper;

    @InjectMocks
    private VectorSearchService service;

    private static AiEmbeddingConfig config() {
        AiEmbeddingConfig config = new AiEmbeddingConfig();
        config.setEnabled(true);
        config.setEmbeddingModel("text-embedding-3");
        config.setDimensions(2);
        config.setOperator("cosine");
        return config;
    }

    @Test
    void blankOrUnauthorisedInputYieldsEmptyWithoutGate() {
        List<UUID> projects = List.of(PROJECT_ID);

        assertTrue(service.search("  ", projects, null, 5, USER_ID).isEmpty());
        assertTrue(service.search("q", List.of(), null, 5, USER_ID).isEmpty());
        assertTrue(service.search("q", null, null, 5, USER_ID).isEmpty());
        assertTrue(service.search("q", projects, null, 0, USER_ID).isEmpty());
        assertTrue(service.search("q", projects, null, -1, USER_ID).isEmpty());

        verifyNoInteractions(gate, embeddingClient, indexMapper);
    }

    @Test
    void searchEmbedsQueryAndSelectsTopKWithinProjects() {
        AiEmbeddingConfig config = config();
        when(gate.requireReady()).thenReturn(config);
        when(embeddingClient.embed(eq(config), eq(List.of("登录失败")), eq(USER_ID), isNull(), isNull()))
                .thenReturn(new AiEmbeddingClient.EmbeddingReply(List.of(0.1, 0.9), 2, 3));
        List<UUID> projects = List.of(PROJECT_ID);
        List<AiVectorSearchHitRespDTO> hits = List.of(new AiVectorSearchHitRespDTO());
        when(indexMapper.selectTopK("[0.1,0.9]", projects, "requirement", 8, "cosine")).thenReturn(hits);

        assertEquals(hits, service.search("登录失败", projects, "requirement", 8, USER_ID));
    }

    @Test
    void gateNotReadyPropagatesWithoutQuerying() {
        when(gate.requireReady())
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.search("q", List.of(PROJECT_ID), null, 5, USER_ID));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code(), ex.getCode());
        verifyNoInteractions(embeddingClient, indexMapper);
    }
}
