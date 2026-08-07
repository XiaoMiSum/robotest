package io.github.xiaomisum.robotest.service.ai.bug;


import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugClusteringStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缺陷聚类发起与查询单测（详细设计 3.3）：无未关闭缺陷返回 6012；发起经任务框架创建；
 * 查询最近一次任务，无记录返回 null。
 */
@ExtendWith(MockitoExtension.class)
class AiBugClusteringServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Mock
    private BugMapper bugMapper;
    @Mock
    private AiTaskService aiTaskService;

    @InjectMocks
    private AiBugClusteringServiceImpl service;

    @Test
    void startClustering_noOpenBugs_throws() {
        when(bugMapper.countOpenBugsByProjectId(PROJECT_ID)).thenReturn(0L);
        assertThrows(ServiceException.class,
                () -> service.startClustering(USER_ID, WORKSPACE_ID, PROJECT_ID));
    }

    @Test
    void startClustering_success_createsTaskWithTargetProjectId() {
        when(bugMapper.countOpenBugsByProjectId(PROJECT_ID)).thenReturn(3L);
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(TASK_ID);
        when(aiTaskService.createTask(Constants.AiTaskType.BUG_CLUSTERING, WORKSPACE_ID, PROJECT_ID, PROJECT_ID, USER_ID))
                .thenReturn(task);

        AiBugClusteringStartRespDTO dto = service.startClustering(USER_ID, WORKSPACE_ID, PROJECT_ID);

        assertEquals(TASK_ID, dto.getTaskId());
        verify(aiTaskService).createTask(Constants.AiTaskType.BUG_CLUSTERING, WORKSPACE_ID, PROJECT_ID, PROJECT_ID, USER_ID);
    }

    @Test
    void getLatestClustering_noRecord_returnsNull() {
        when(aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.BUG_CLUSTERING, PROJECT_ID, PROJECT_ID))
                .thenReturn(null);
        assertNull(service.getLatestClustering(PROJECT_ID));
    }

    @Test
    void getLatestClustering_returnsLatestTask() {
        AiTaskRespDTO dto = new AiTaskRespDTO();
        dto.setId(TASK_ID);
        when(aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.BUG_CLUSTERING, PROJECT_ID, PROJECT_ID))
                .thenReturn(dto);
        assertEquals(TASK_ID, service.getLatestClustering(PROJECT_ID).getId());
    }
}
