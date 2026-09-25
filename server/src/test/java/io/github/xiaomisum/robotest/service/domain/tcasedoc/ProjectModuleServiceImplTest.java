package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 模块目录路径资源归属活动项目校验（backlog SEC-014） */
@ExtendWith(MockitoExtension.class)
class ProjectModuleServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();

    @Mock
    private ProjectModuleMapper projectModuleMapper;

    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ProjectModuleServiceImpl service;

    private ProjectModule module(UUID projectId) {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setProjectId(projectId);
        return module;
    }

    @Test
    void updateModule_crossProject_throwsNotFound() {
        when(projectModuleMapper.selectById(MODULE_ID)).thenReturn(module(UUID.randomUUID()));

        // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露模块存在性
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateModule(PROJECT_ID, MODULE_ID, USER_ID, new ProjectModuleUpdateReqDTO()));
        assertEquals(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND.code(), exception.getCode());
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void deleteModule_crossProject_throwsNotFound() {
        when(projectModuleMapper.selectById(MODULE_ID)).thenReturn(module(UUID.randomUUID()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteModule(PROJECT_ID, MODULE_ID, USER_ID));
        assertEquals(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND.code(), exception.getCode());
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }
}
