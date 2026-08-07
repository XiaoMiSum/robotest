package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseModuleTreeRespDTO;

import java.util.List;
import java.util.UUID;

public interface TestCaseModuleService {

    List<TestCaseModuleTreeRespDTO> getModuleTree(UUID projectId, UUID userId);

    TestCaseModuleTreeRespDTO createModule(UUID projectId, UUID userId, TestCaseModuleCreateReqDTO reqDTO);

    TestCaseModuleTreeRespDTO updateModule(UUID moduleId, UUID userId, TestCaseModuleUpdateReqDTO reqDTO);

    void deleteModule(UUID moduleId, UUID userId);
}
