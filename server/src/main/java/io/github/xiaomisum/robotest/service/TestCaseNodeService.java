package io.github.xiaomisum.robotest.service;

import io.github.xiaomisum.robotest.model.dto.response.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;

public interface TestCaseNodeService {

    TestCaseDocumentNodesRespDTO getDocumentNodes(String documentId);

    TestCaseNodeTreeRespDTO getCaseDetail(String caseId);
}
