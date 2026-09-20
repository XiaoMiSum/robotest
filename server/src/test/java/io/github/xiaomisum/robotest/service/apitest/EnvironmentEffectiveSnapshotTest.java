package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentEffectiveSnapshotTest {

    @Mock
    private MultipartFile file;

    @Test
    void parseImportPayload_validJson_returnsPayload() throws Exception {
        String json = "{\"id\":\"1\",\"name\":\"Test Env\",\"description\":\"Desc\"}";
        when(file.getBytes()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        ApiEnvironmentDetailRespDTO result = EnvironmentEffectiveSnapshot.parseImportPayload(file);

        assertThat(result.getName()).isEqualTo("Test Env");
        assertThat(result.getDescription()).isEqualTo("Desc");
    }

    @Test
    void parseImportPayload_nullPayload_throwsValidationFailed() throws Exception {
        when(file.getBytes()).thenReturn("null".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> EnvironmentEffectiveSnapshot.parseImportPayload(file))
                .isInstanceOf(xyz.migoo.framework.common.exception.ServiceException.class);
    }

    @Test
    void parseImportPayload_invalidJson_throwsValidationFailed() throws Exception {
        when(file.getBytes()).thenReturn("{invalid json}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> EnvironmentEffectiveSnapshot.parseImportPayload(file))
                .isInstanceOf(xyz.migoo.framework.common.exception.ServiceException.class);
    }
}