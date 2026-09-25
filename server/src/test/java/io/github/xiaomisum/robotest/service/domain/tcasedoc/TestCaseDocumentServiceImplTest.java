package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
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

/** 文档路径资源归属活动项目校验（backlog SEC-014） */
@ExtendWith(MockitoExtension.class)
class TestCaseDocumentServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;

    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private TestCaseDocumentServiceImpl service;

    private TestCaseDocument document(UUID projectId) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(projectId);
        return document;
    }

    @Test
    void updateTestCase_crossProject_throwsNotFound() {
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document(UUID.randomUUID()));

        // 归属活动项目校验（SEC-014）：跨项目按不存在处理，不泄露文档存在性
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateTestCase(PROJECT_ID, DOCUMENT_ID, USER_ID,
                        new TestCaseDocumentUpdateReqDTO()));
        assertEquals(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND.code(), exception.getCode());
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }

    @Test
    void deleteTestCase_crossProject_throwsNotFound() {
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document(UUID.randomUUID()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteTestCase(PROJECT_ID, DOCUMENT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND.code(), exception.getCode());
        verify(projectAccessGuard, never()).requireProjectMember(any(), any());
    }
}
