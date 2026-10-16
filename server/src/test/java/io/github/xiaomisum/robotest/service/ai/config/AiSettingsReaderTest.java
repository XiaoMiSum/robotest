package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSettingsReaderTest {

    private static final UUID MODEL_ID = UUID.randomUUID();

    @Mock
    private AiConfigMapper configMapper;
    @Mock
    private AiModelConfigMapper modelMapper;

    @InjectMocks
    private AiSettingsReader reader;

    private static AiConfig configRow(boolean enabled, UUID defaultModelId, Integer timeout, Integer retries) {
        AiConfig row = new AiConfig();
        row.setEnabled(enabled);
        row.setDefaultModelId(defaultModelId);
        row.setTaskTimeoutSeconds(timeout);
        row.setTaskMaxRetries(retries);
        return row;
    }

    private static AiModelConfig model(boolean enabled) {
        AiModelConfig model = new AiModelConfig();
        model.setId(MODEL_ID);
        model.setEnabled(enabled);
        return model;
    }

    @Test
    void settings_rowMissing_returnsBuiltInDefaults() {
        when(configMapper.selectSingleton()).thenReturn(null);

        AiSettingsReader.AiSettings settings = reader.settings();

        assertFalse(settings.enabled());
        assertNull(settings.defaultModelId());
        assertEquals(600, settings.taskTimeoutSeconds());
        assertEquals(2, settings.taskMaxRetries());
    }

    @Test
    void settings_rowPresent_returnsRowValues() {
        when(configMapper.selectSingleton())
                .thenReturn(configRow(true, MODEL_ID, 900, 3));

        AiSettingsReader.AiSettings settings = reader.settings();

        assertTrue(settings.enabled());
        assertEquals(MODEL_ID, settings.defaultModelId());
        assertEquals(900, settings.taskTimeoutSeconds());
        assertEquals(3, settings.taskMaxRetries());
    }

    @Test
    void usableModel_defaultModelEnabled_returnsModel() {
        when(configMapper.selectSingleton()).thenReturn(configRow(true, MODEL_ID, null, null));
        AiModelConfig expected = model(true);
        when(modelMapper.selectById(MODEL_ID)).thenReturn(expected);

        assertSame(expected, reader.usableModel());
    }

    @Test
    void usableModel_defaultModelDisabledOrMissing_returnsNull() {
        when(configMapper.selectSingleton()).thenReturn(configRow(true, MODEL_ID, null, null));
        when(modelMapper.selectById(MODEL_ID)).thenReturn(model(false));

        assertNull(reader.usableModel());
    }

    @Test
    void usableModel_noDefaultModel_returnsNull() {
        when(configMapper.selectSingleton()).thenReturn(configRow(true, null, null, null));

        assertNull(reader.usableModel());
    }

    @Test
    void resolveModel_preferredStillEnabled_reusesIt() {
        AiModelConfig preferred = model(true);
        when(modelMapper.selectById(MODEL_ID)).thenReturn(preferred);

        assertSame(preferred, reader.resolveModel(MODEL_ID));
    }

    @Test
    void resolveModel_preferredDisabled_fallsBackToUsable() {
        when(modelMapper.selectById(MODEL_ID)).thenReturn(model(false));
        when(configMapper.selectSingleton()).thenReturn(configRow(true, null, null, null));

        assertNull(reader.resolveModel(MODEL_ID));
    }

    private static AiModelConfig modelWith(String capability, int priority) {
        AiModelConfig model = new AiModelConfig();
        model.setId(UUID.randomUUID());
        model.setEnabled(true);
        model.setCapabilities(capability == null ? List.of() : List.of(capability));
        model.setPriority(priority);
        return model;
    }

    @Test
    void resolveModel_capabilityRequired_preferredLackingCapability_scansCandidates() {
        AiModelConfig preferred = new AiModelConfig();
        preferred.setId(MODEL_ID);
        preferred.setEnabled(true);
        when(modelMapper.selectById(MODEL_ID)).thenReturn(preferred);
        when(configMapper.selectSingleton()).thenReturn(configRow(true, null, null, null));
        AiModelConfig vision = modelWith("vision", 50);
        when(modelMapper.selectList(any())).thenReturn(List.of(vision));

        assertSame(vision, reader.resolveModel(MODEL_ID, AiSettingsReader.CAPABILITY_VISION));
    }

    @Test
    void resolveModel_capabilityRequired_defaultHasCapability_returnsDefault() {
        AiModelConfig defaultModel = new AiModelConfig();
        defaultModel.setId(MODEL_ID);
        defaultModel.setEnabled(true);
        defaultModel.setCapabilities(List.of("vision"));
        when(configMapper.selectSingleton()).thenReturn(configRow(true, MODEL_ID, null, null));
        when(modelMapper.selectById(MODEL_ID)).thenReturn(defaultModel);

        assertSame(defaultModel, reader.resolveModel(null, AiSettingsReader.CAPABILITY_VISION));
    }

    @Test
    void resolveModel_capabilityRequired_scanTakesFirstCapableInPriorityOrder() {
        when(configMapper.selectSingleton()).thenReturn(configRow(true, null, null, null));
        // SQL 侧按 priority 升序返回：无能力(10) → vision(50) → vision(200)
        AiModelConfig noVision = modelWith(null, 10);
        AiModelConfig lowPriority = modelWith("vision", 50);
        AiModelConfig highPriority = modelWith("vision", 200);
        when(modelMapper.selectList(any())).thenReturn(List.of(noVision, lowPriority, highPriority));

        assertSame(lowPriority, reader.resolveModel(null, AiSettingsReader.CAPABILITY_VISION));
    }

    @Test
    void resolveModel_capabilityRequired_noCandidate_returnsNull() {
        when(configMapper.selectSingleton()).thenReturn(configRow(true, null, null, null));
        when(modelMapper.selectList(any())).thenReturn(List.of(modelWith(null, 10)));

        assertNull(reader.resolveModel(null, AiSettingsReader.CAPABILITY_VISION));
    }

    @Test
    void status_availableOnlyWhenEnabledAndModelReady() {
        when(configMapper.selectSingleton()).thenReturn(configRow(true, MODEL_ID, null, null));
        when(modelMapper.selectById(MODEL_ID)).thenReturn(model(true));

        AiStatusRespDTO status = reader.status();

        assertTrue(status.getEnabled());
        assertTrue(status.getModelReady());
        assertTrue(status.getAvailable());
    }

    @Test
    void status_switchOff_availableFalse() {
        when(configMapper.selectSingleton()).thenReturn(configRow(false, null, null, null));

        AiStatusRespDTO status = reader.status();

        assertFalse(status.getEnabled());
        assertFalse(status.getModelReady());
        assertFalse(status.getAvailable());
    }
}
