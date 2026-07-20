package io.github.xiaomisum.robotest.service;

import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;

import java.util.List;

public interface TestCaseModuleService {

    List<TestCaseModuleTreeRespDTO> getModuleTree(String projectId);

    TestCaseModuleTreeRespDTO createModule(String projectId, TestCaseModuleCreateReqDTO reqDTO);

    TestCaseModuleTreeRespDTO updateModule(String moduleId, TestCaseModuleUpdateReqDTO reqDTO);

    void deleteModule(String moduleId);
}
