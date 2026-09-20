package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneSettingsValidatorTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Test
    void buildCreateScene_populatesAllFields() {
        ApiSceneCreateReqDTO req = new ApiSceneCreateReqDTO();
        req.setName("New Scene");
        req.setModuleId(UUID.randomUUID());
        req.setDescription("Desc");
        req.setEnvironmentId(UUID.randomUUID());
        req.setPriority("P0");
        req.setStatus("draft");
        req.setProcessors(List.of(Map.of("id", UUID.randomUUID().toString())));
        
        ApiSceneStepSaveReqDTO step = new ApiSceneStepSaveReqDTO();
        step.setName("Step 1");
        step.setStepType("http");
        req.setSteps(List.of(step));
        
        req.setVariables(List.of(new ApiSceneVariableBatchReqDTO.Variable() {{
            setName("var1"); setValue("val"); setDescription("desc");
        }}));

        ApiScene scene = SceneSettingsValidator.buildCreateScene(PROJECT_ID, req);

        assertThat(scene.getId()).isNotNull();
        assertThat(scene.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(scene.getName()).isEqualTo("New Scene");
        assertThat(scene.getModuleId()).isEqualTo(req.getModuleId());
        assertThat(scene.getDescription()).isEqualTo("Desc");
        assertThat(scene.getEnvironmentId()).isEqualTo(req.getEnvironmentId());
        assertThat(scene.getPriority()).isEqualTo("P0");
        assertThat(scene.getStatus()).isEqualTo("draft");
        assertThat(scene.getProcessors()).hasSize(1);
        assertThat(scene.getSteps()).hasSize(1);
        assertThat(scene.getVariables()).hasSize(1);
        assertThat(scene.getChangeVersion()).isEqualTo(1);
    }

    @Test
    void buildCreateScene_defaultsStatusToDraft() {
        ApiSceneCreateReqDTO req = new ApiSceneCreateReqDTO();
        req.setName("Scene");
        req.setStatus(null);

        ApiScene scene = SceneSettingsValidator.buildCreateScene(PROJECT_ID, req);

        assertThat(scene.getStatus()).isEqualTo("draft");
    }

    @Test
    void buildCreateScene_defaultsProcessorsToEmpty() {
        ApiSceneCreateReqDTO req = new ApiSceneCreateReqDTO();
        req.setName("Scene");
        req.setProcessors(null);

        ApiScene scene = SceneSettingsValidator.buildCreateScene(PROJECT_ID, req);

        assertThat(scene.getProcessors()).isNotNull().isEmpty();
    }

    @Test
    void buildCreateScene_nullStepsAndVariables_handled() {
        ApiSceneCreateReqDTO req = new ApiSceneCreateReqDTO();
        req.setName("Scene");
        req.setSteps(null);
        req.setVariables(null);

        ApiScene scene = SceneSettingsValidator.buildCreateScene(PROJECT_ID, req);

        assertThat(scene.getSteps()).isNotNull();
        assertThat(scene.getVariables()).isNotNull();
    }

    @Test
    void buildUpdateCarrier_carriesOnlyChangedFields() {
        ApiSceneUpdateReqDTO req = new ApiSceneUpdateReqDTO();
        req.setName("Updated");
        req.setDescription("New desc");
        req.setPriority("P1");
        req.setStatus("active");

        ApiScene carrier = SceneSettingsValidator.buildUpdateCarrier(UUID.randomUUID(), req, 5);

        assertThat(carrier.getId()).isNotNull();
        assertThat(carrier.getName()).isEqualTo("Updated");
        assertThat(carrier.getDescription()).isEqualTo("New desc");
        assertThat(carrier.getPriority()).isEqualTo("P1");
        assertThat(carrier.getStatus()).isEqualTo("active");
        assertThat(carrier.getChangeVersion()).isEqualTo(5);
    }

    @Test
    void buildUpdateCarrier_variablesNull_doesNotSet() {
        ApiSceneUpdateReqDTO req = new ApiSceneUpdateReqDTO();
        req.setVariables(null);

        ApiScene carrier = SceneSettingsValidator.buildUpdateCarrier(UUID.randomUUID(), req, 2);

        assertThat(carrier.getVariables()).isNull();
    }

    @Test
    void buildUpdateCarrier_variablesProvided_normalizes() {
        ApiSceneUpdateReqDTO req = new ApiSceneUpdateReqDTO();
        req.setVariables(List.of(new ApiSceneVariableBatchReqDTO.Variable() {{
            setName("v1"); setValue("1");
        }}));

        ApiScene carrier = SceneSettingsValidator.buildUpdateCarrier(UUID.randomUUID(), req, 2);

        assertThat(carrier.getVariables()).hasSize(1);
    }
}