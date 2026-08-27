package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.service.project.TestCaseDocumentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/testcases")
public class TestCaseDocumentController {

    @Resource
    private TestCaseDocumentService testCaseDocumentService;

    @GetMapping
    @PreAuthorize("hasAuthority('case:view')")
    public Result<List<TestCaseDocumentRespDTO>> getTestCaseList(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(required = false) UUID moduleId) {
        return Result.ok(testCaseDocumentService.getTestCaseList(projectId, loginUser.getId(), moduleId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('case:edit')")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TestCaseDocumentRespDTO> createTestCase(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid TestCaseDocumentCreateReqDTO reqDTO) {
        return Result.ok(testCaseDocumentService.createTestCase(projectId, loginUser.getId(), reqDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('case:edit')")
    public Result<TestCaseDocumentRespDTO> updateTestCase(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid TestCaseDocumentUpdateReqDTO reqDTO) {
        return Result.ok(testCaseDocumentService.updateTestCase(id, loginUser.getId(), reqDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('case:edit')")
    public Result<Void> deleteTestCase(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        testCaseDocumentService.deleteTestCase(id, loginUser.getId());
        return Result.ok();
    }
}
