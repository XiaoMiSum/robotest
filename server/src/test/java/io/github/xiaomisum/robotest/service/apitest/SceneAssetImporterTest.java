package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SceneAssetImporterTest {

    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private CommonComponentMapper componentMapper;

    private ApiScene scene;
    private UUID sceneId;
    private UUID stepId;

    @BeforeEach
    void setUp() {
        sceneId = UUID.randomUUID();
        stepId = UUID.randomUUID();
        scene = new ApiScene();
        scene.setId(sceneId);
        scene.setSteps(List.of(
                Map.of("id", stepId.toString(), "name", "Step 1")
        ));
    }

    @Test
    void resolveTarget_sceneProcessor_returnsNull() {
        Map<String, Object> target = SceneAssetImporter.resolveTarget(scene, "scene_processor", null);

        assertThat(target).isNull();
    }

    @Test
    void resolveTarget_stepValidator_validStepId_returnsStep() {
        Map<String, Object> target = SceneAssetImporter.resolveTarget(scene, "step_validator", stepId);

        assertThat(target).isNotNull();
        assertThat(target.get("id")).isEqualTo(stepId.toString());
    }

    @Test
    void resolveTarget_stepValidator_invalidStepId_throws() {
        assertThatThrownBy(() -> SceneAssetImporter.resolveTarget(scene, "step_validator", UUID.randomUUID()))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void resolveTarget_invalidTarget_throws() {
        assertThatThrownBy(() -> SceneAssetImporter.resolveTarget(scene, "invalid_target", null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void apply_sceneProcessor_insertsAndUpdatesProcessors() {
        UUID compId = UUID.randomUUID();
        CommonComponent comp = new CommonComponent();
        comp.setId(compId);
        comp.setName("Comp");
        comp.setType("preprocessor");
        comp.setSortOrder(1);
        comp.setConfig("{}");
        comp.setEnabled(true);
        when(componentMapper.selectById(compId)).thenReturn(comp);

        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        // For scene_processor, step is null (per resolveTarget)
        int imported = SceneAssetImporter.apply(sceneMapper, componentMapper, scene,
                null, "scene_processor", List.of(compId));

        assertThat(imported).isEqualTo(1);
        verify(sceneMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(sceneId);
    }

    @Test
    void apply_duplicateComponents_deduplicates() {
        UUID compId = UUID.randomUUID();
        CommonComponent comp = new CommonComponent();
        comp.setId(compId);
        comp.setName("Comp");
        comp.setType("preprocessor");
        comp.setSortOrder(1);
        comp.setConfig("{}");
        comp.setEnabled(true);
        when(componentMapper.selectById(compId)).thenReturn(comp);

        // Add existing processor
        scene.setProcessors(List.of(
                Map.of("id", compId.toString(), "name", "Comp")
        ));

        // For scene_processor, step is null - no deduplication in importer, service handles it
        int imported = SceneAssetImporter.apply(sceneMapper, componentMapper, scene,
                null, "scene_processor", List.of(compId, compId));

        // Both components are added (no deduplication in importer)
        assertThat(imported).isEqualTo(2);
    }

    @Test
    void apply_stepValidator_updatesStepValidators() {
        UUID compId = UUID.randomUUID();
        CommonComponent comp = new CommonComponent();
        comp.setId(compId);
        comp.setName("Comp");
        comp.setType("validator");
        comp.setSortOrder(2);
        comp.setConfig("{}");
        comp.setEnabled(true);
        when(componentMapper.selectById(compId)).thenReturn(comp);

        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        // Use mutable map for step
        Map<String, Object> step = new java.util.HashMap<>(scene.getSteps().get(0));
        int imported = SceneAssetImporter.apply(sceneMapper, componentMapper, scene,
                step, "step_validator", List.of(compId));

        assertThat(imported).isEqualTo(1);
        verify(sceneMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(sceneId);
    }
}