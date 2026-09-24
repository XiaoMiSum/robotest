package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScenePageItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneFollow;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneDetailAssemblerTest {

    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiSceneFollowMapper sceneFollowMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void fetchPage_emptyResult_returnsEmptyPage() {
        when(sceneMapper.selectPage(any(), any(), any(), any(), any(), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        PageResult<ApiScenePageItemRespDTO> result = SceneDetailAssembler.fetchPage(
                sceneMapper, sceneFollowMapper, executionRecordMapper,
                projectId, null, null, null, null,
                new PageParam() {{ setPageNo(1); setPageSize(10); }},
                userId);

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void fetchPage_withScenes_mapsFieldsAndFlags() {
        UUID sceneId = UUID.randomUUID();
        ApiScene scene = new ApiScene();
        scene.setId(sceneId);
        scene.setName("Test Scene");
        scene.setDescription("Desc");
        scene.setModuleId(UUID.randomUUID());
        scene.setEnvironmentId(UUID.randomUUID());
        scene.setPriority("P0");
        scene.setStatus("active");
        scene.setSteps(List.of(Map.of(), Map.of()));
        scene.setCreatedAt(LocalDateTime.now());
        scene.setUpdatedAt(LocalDateTime.now());

        when(sceneMapper.selectPage(any(), any(), any(), any(), any(), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(scene), 1L));
        when(sceneMapper.selectBatchIds(anyList())).thenReturn(List.of(scene));
        when(sceneFollowMapper.selectFollowedSceneIdsByUserId(userId))
                .thenReturn(List.of(sceneId));
        ApiExecutionRecord execution = new ApiExecutionRecord();
        execution.setSceneId(sceneId);
        execution.setStatus("success");
        execution.setExecutedAt(LocalDateTime.now());
        when(executionRecordMapper.listLatestBySceneIds(anyList()))
                .thenReturn(List.of(execution));

        PageResult<ApiScenePageItemRespDTO> result = SceneDetailAssembler.fetchPage(
                sceneMapper, sceneFollowMapper, executionRecordMapper,
                projectId, null, null, null, null,
                new PageParam() {{ setPageNo(1); setPageSize(10); }},
                userId);

        assertThat(result.getList()).hasSize(1);
        ApiScenePageItemRespDTO dto = result.getList().get(0);
        assertThat(dto.getId()).isEqualTo(sceneId);
        assertThat(dto.getName()).isEqualTo("Test Scene");
        assertThat(dto.getStepCount()).isEqualTo(2);
        assertThat(dto.getFollowed()).isTrue();
        assertThat(dto.getLastStatus()).isEqualTo("success");
    }

    @Test
    void fetchPage_withNullSteps_treatsAsZero() {
        ApiScene scene = new ApiScene();
        scene.setId(UUID.randomUUID());
        scene.setSteps(null);
        scene.setUpdatedAt(LocalDateTime.now());

        when(sceneMapper.selectPage(any(), any(), any(), any(), any(), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(scene), 1L));
        when(sceneFollowMapper.selectFollowedSceneIdsByUserId(userId)).thenReturn(List.of());
        when(executionRecordMapper.listLatestBySceneIds(anyList())).thenReturn(List.of());

        PageResult<ApiScenePageItemRespDTO> result = SceneDetailAssembler.fetchPage(
                sceneMapper, sceneFollowMapper, executionRecordMapper,
                projectId, null, null, null, null,
                new PageParam() {{ setPageNo(1); setPageSize(10); }},
                userId);

        assertThat(result.getList().get(0).getStepCount()).isZero();
    }
}