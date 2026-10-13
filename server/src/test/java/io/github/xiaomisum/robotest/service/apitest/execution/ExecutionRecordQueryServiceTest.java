package io.github.xiaomisum.robotest.service.apitest.execution;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiExecutionStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执行状态查询（WP-6.2，GitLab 集成详设 3.3）：CI 执行经服务端轮询回写后，
 * 前端按同一接口读单条记录状态；归属校验与场景删除降级。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionRecordQueryServiceTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private SysUserMapper userMapper;

    private ExecutionRecordQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new ExecutionRecordQueryService();
        ReflectionTestUtils.setField(queryService, "projectAccessGuard", projectAccessGuard);
        ReflectionTestUtils.setField(queryService, "executionRecordMapper", executionRecordMapper);
        ReflectionTestUtils.setField(queryService, "changeHistoryMapper", changeHistoryMapper);
        ReflectionTestUtils.setField(queryService, "sceneMapper", sceneMapper);
        ReflectionTestUtils.setField(queryService, "userMapper", userMapper);
    }

    private ApiExecutionRecord record(UUID executionId, UUID sceneId) {
        ApiExecutionRecord record = new ApiExecutionRecord();
        record.setId(executionId);
        record.setProjectId(PROJECT_ID);
        record.setSceneId(sceneId);
        record.setStatus("running");
        record.setExecutionMode("platform");
        record.setTriggerType("manual");
        record.setExecutedAt(LocalDateTime.now());
        return record;
    }

    @Test
    void getStatusProjectsSingleRecordWithSceneName() {
        UUID executionId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        ApiExecutionRecord record = record(executionId, sceneId);
        ApiScene scene = new ApiScene();
        scene.setId(sceneId);
        scene.setName("登录场景");
        when(executionRecordMapper.selectById(executionId)).thenReturn(record);
        when(sceneMapper.selectById(sceneId)).thenReturn(scene);

        ApiExecutionStatusRespDTO dto =
                queryService.getStatus(WORKSPACE_ID, PROJECT_ID, USER_ID, executionId);

        assertEquals(executionId.toString(), dto.getId());
        assertEquals("running", dto.getStatus());
        assertEquals(sceneId.toString(), dto.getSceneId());
        assertEquals("登录场景", dto.getSceneName());
        assertEquals("manual", dto.getTriggerType());
        assertNull(dto.getReportId());
        verify(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);
    }

    @Test
    void getStatusRejectsRecordOfOtherProject() {
        UUID executionId = UUID.randomUUID();
        ApiExecutionRecord record = record(executionId, UUID.randomUUID());
        record.setProjectId(UUID.randomUUID());
        when(executionRecordMapper.selectById(executionId)).thenReturn(record);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> queryService.getStatus(WORKSPACE_ID, PROJECT_ID, USER_ID, executionId));

        // 执行记录不存在与跨项目越权同码，不泄露他项目记录存在性
        assertEquals(1000017331, ex.getCode());
    }

    @Test
    void getStatusDegradesWhenSceneDeleted() {
        UUID executionId = UUID.randomUUID();
        UUID sceneId = UUID.randomUUID();
        when(executionRecordMapper.selectById(executionId)).thenReturn(record(executionId, sceneId));
        when(sceneMapper.selectById(sceneId)).thenReturn(null);

        ApiExecutionStatusRespDTO dto =
                queryService.getStatus(WORKSPACE_ID, PROJECT_ID, USER_ID, executionId);

        assertEquals("running", dto.getStatus());
        assertNull(dto.getSceneName());
    }
}
