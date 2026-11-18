package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseRequirementsUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseRequirementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project")
public class TestCaseNodeController {

    @Resource
    private TestCaseNodeService testCaseNodeService;
    @Resource
    private TestCaseRequirementService testCaseRequirementService;

    @GetMapping("/documents/{docId}/nodes")
    public Result<TestCaseDocumentNodesRespDTO> getDocumentNodes(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID docId) {
        return Result.ok(testCaseNodeService.getDocumentNodes(loginUser.getActiveProjectId(), docId, loginUser.getId()));
    }

    @GetMapping("/cases/{caseId}")
    public Result<TestCaseNodeTreeRespDTO> getCaseDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID caseId) {
        return Result.ok(testCaseNodeService.getCaseDetail(loginUser.getActiveProjectId(), caseId, loginUser.getId()));
    }

    @GetMapping("/cases")
    public Result<PageResult<TestCaseCaseListRespDTO>> getCaseList(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(testCaseNodeService.getCaseList(loginUser.getActiveProjectId(), loginUser.getId(), keyword, priority, pageNo, pageSize));
    }

    @PutMapping("/cases/{caseId}")
    public Result<Void> updateCaseNode(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID caseId,
            @RequestBody @Valid TestCaseNodeUpdateReqDTO reqDTO) {
        testCaseNodeService.updateCaseNode(loginUser.getActiveProjectId(), caseId, loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @GetMapping("/cases/{caseId}/requirements")
    public Result<List<RequirementRefRespDTO>> getCaseRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID caseId) {
        return Result.ok(testCaseRequirementService.listRequirements(
                loginUser.getActiveProjectId(), caseId, loginUser.getId()));
    }

    @PutMapping("/cases/{caseId}/requirements")
    public Result<List<RequirementRefRespDTO>> setCaseRequirements(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID caseId,
            @RequestBody @Valid TestCaseRequirementsUpdateReqDTO reqDTO) {
        return Result.ok(testCaseRequirementService.saveRequirements(
                loginUser.getActiveProjectId(), caseId, reqDTO.getRequirementIds(), loginUser.getId()));
    }
}
