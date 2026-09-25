package io.github.xiaomisum.robotest.service.ai.recommend;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** 计划执行顺序推荐：路径 planId 归属活动项目与执行人口径（backlog SEC-014） */
@ExtendWith(MockitoExtension.class)
class AiPlanOrderRecommendServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @Mock
    private TestPlanMapper testPlanMapper;

    @InjectMocks
    private AiPlanOrderRecommendServiceImpl service;

    private TestPlan plan(UUID projectId, UUID executorId) {
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setProjectId(projectId);
        plan.setExecutorId(executorId);
        return plan;
    }

    @Test
    void compute_crossProject_throwsPlanNotFound() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(UUID.randomUUID(), USER_ID));
        // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露计划存在性
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.compute(USER_ID, WORKSPACE_ID, PROJECT_ID, PLAN_ID));
        assertEquals(ErrorCodeConstants.TEST_PLAN_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void compute_notExecutor_throwsNoPermission() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(PROJECT_ID, UUID.randomUUID()));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.compute(USER_ID, WORKSPACE_ID, PROJECT_ID, PLAN_ID));
        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void query_crossProject_throwsPlanNotFound() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(UUID.randomUUID(), USER_ID));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.query(USER_ID, WORKSPACE_ID, PROJECT_ID, PLAN_ID));
        assertEquals(ErrorCodeConstants.TEST_PLAN_NOT_FOUND.code(), exception.getCode());
    }
}
