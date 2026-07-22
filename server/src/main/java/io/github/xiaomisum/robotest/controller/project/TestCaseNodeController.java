package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.service.project.TestCaseNodeService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
public class TestCaseNodeController {

    @Resource
    private TestCaseNodeService testCaseNodeService;

    @GetMapping("/documents/{docId}/nodes")
    public TestCaseDocumentNodesRespDTO getDocumentNodes(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @PathVariable String docId) {
        return testCaseNodeService.getDocumentNodes(docId);
    }

    @GetMapping("/cases/{caseId}")
    public TestCaseNodeTreeRespDTO getCaseDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @PathVariable String caseId) {
        return testCaseNodeService.getCaseDetail(caseId);
    }
}
