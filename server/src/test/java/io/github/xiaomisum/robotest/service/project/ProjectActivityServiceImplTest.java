package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectActivityRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.ProjectActivity;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectActivityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectActivityServiceImplTest {

    @Mock
    private ProjectActivityMapper projectActivityMapper;
    @Mock
    private SysUserMapper userMapper;
    @InjectMocks
    private ProjectActivityServiceImpl activityService;

    @Test
    void record_persistsActorAndResourceSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        SysUser actor = new SysUser();
        actor.setName("测试用户");
        when(userMapper.selectById(actorId)).thenReturn(actor);

        activityService.record(projectId, actorId, "TEST_PLAN", resourceId,
                "回归计划", "PLAN_CREATED", "创建测试计划「回归计划」");

        ArgumentCaptor<ProjectActivity> captor = ArgumentCaptor.forClass(ProjectActivity.class);
        verify(projectActivityMapper).insert(captor.capture());
        ProjectActivity activity = captor.getValue();
        assertEquals(projectId, activity.getProjectId());
        assertEquals(actorId, activity.getActorId());
        assertEquals("测试用户", activity.getActorName());
        assertEquals("TEST_PLAN", activity.getResourceType());
        assertEquals(resourceId, activity.getResourceId());
        assertEquals("回归计划", activity.getResourceName());
        assertEquals("PLAN_CREATED", activity.getAction());
        assertNotNull(activity.getOccurredAt());
    }

    @Test
    void record_failureDoesNotBreakBusinessFlow() {
        doThrow(new IllegalStateException("db unavailable"))
                .when(projectActivityMapper).insert(org.mockito.ArgumentMatchers.any(ProjectActivity.class));

        assertDoesNotThrow(() -> activityService.record(UUID.randomUUID(), UUID.randomUUID(),
                "PROJECT", UUID.randomUUID(), "项目", "PROJECT_CREATED", "创建项目"));
    }

    @Test
    void listRecent_mapsEntitiesToResponses() {
        UUID activityId = UUID.randomUUID();
        ProjectActivity activity = new ProjectActivity();
        activity.setId(activityId);
        activity.setProjectId(UUID.randomUUID());
        activity.setActorId(UUID.randomUUID());
        activity.setActorName("用户");
        activity.setResourceType("BUG");
        activity.setResourceId(UUID.randomUUID());
        activity.setResourceName("缺陷");
        activity.setAction("BUG_CREATED");
        activity.setSummary("提交缺陷");
        activity.setOccurredAt(LocalDateTime.now());
        when(projectActivityMapper.findRecentByProjectId(activity.getProjectId(), 8))
                .thenReturn(List.of(activity));

        List<ProjectActivityRespDTO> result = activityService.listRecent(activity.getProjectId(), 8);

        assertEquals(1, result.size());
        assertEquals(activityId, result.get(0).getId());
        assertEquals("提交缺陷", result.get(0).getSummary());
    }
}
