package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;

import java.util.List;
import java.util.UUID;

public interface TestCaseDocumentService {

    List<TestCaseDocumentRespDTO> getTestCaseList(UUID projectId, UUID userId, UUID moduleId);

    TestCaseDocumentRespDTO createTestCase(UUID projectId, UUID userId, TestCaseDocumentCreateReqDTO reqDTO);

    TestCaseDocumentRespDTO updateTestCase(UUID documentId, UUID userId, TestCaseDocumentUpdateReqDTO reqDTO);

    void deleteTestCase(UUID documentId, UUID userId);
}
