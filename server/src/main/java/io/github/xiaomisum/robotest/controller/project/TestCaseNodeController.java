package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.service.project.TestCaseNodeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;

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

    @GetMapping("/cases")
    public PageResult<TestCaseCaseListRespDTO> getCaseList(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return testCaseNodeService.getCaseList(projectId, keyword, priority, pageNo, pageSize);
    }

    @PutMapping("/cases/{caseId}")
    public void updateCaseNode(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @PathVariable String caseId,
            @RequestBody @Valid TestCaseNodeUpdateReqDTO reqDTO) {
        testCaseNodeService.updateCaseNode(caseId, reqDTO);
    }
}
