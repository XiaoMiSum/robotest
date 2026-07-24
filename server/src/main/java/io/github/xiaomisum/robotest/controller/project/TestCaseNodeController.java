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
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project")
public class TestCaseNodeController {

    @Resource
    private TestCaseNodeService testCaseNodeService;

    @GetMapping("/documents/{docId}/nodes")
    public Result<TestCaseDocumentNodesRespDTO> getDocumentNodes(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @PathVariable UUID docId) {
        return Result.ok(testCaseNodeService.getDocumentNodes(docId));
    }

    @GetMapping("/cases/{caseId}")
    public Result<TestCaseNodeTreeRespDTO> getCaseDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @PathVariable UUID caseId) {
        return Result.ok(testCaseNodeService.getCaseDetail(caseId));
    }

    @GetMapping("/cases")
    public Result<PageResult<TestCaseCaseListRespDTO>> getCaseList(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(testCaseNodeService.getCaseList(projectId, keyword, priority, pageNo, pageSize));
    }

    @PutMapping("/cases/{caseId}")
    public Result<Void> updateCaseNode(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @PathVariable UUID caseId,
            @RequestBody @Valid TestCaseNodeUpdateReqDTO reqDTO) {
        testCaseNodeService.updateCaseNode(caseId, reqDTO);
        return Result.ok();
    }
}
