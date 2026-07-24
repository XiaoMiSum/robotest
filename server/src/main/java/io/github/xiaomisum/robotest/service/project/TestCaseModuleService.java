package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;

import java.util.List;
import java.util.UUID;

public interface TestCaseModuleService {

    List<TestCaseModuleTreeRespDTO> getModuleTree(String projectId);

    TestCaseModuleTreeRespDTO createModule(String projectId, TestCaseModuleCreateReqDTO reqDTO);

    TestCaseModuleTreeRespDTO updateModule(UUID moduleId, TestCaseModuleUpdateReqDTO reqDTO);

    void deleteModule(UUID moduleId);
}
