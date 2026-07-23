package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.service.project.TestCaseModuleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

@RestController
@RequestMapping("/api/project/modules")
public class TestCaseModuleController {

    @Resource
    private TestCaseModuleService testCaseModuleService;

    @GetMapping
    public Result<List<TestCaseModuleTreeRespDTO>> getModuleTree(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId) {
        return Result.ok(testCaseModuleService.getModuleTree(projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TestCaseModuleTreeRespDTO> createModule(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId,
            @RequestBody @Valid TestCaseModuleCreateReqDTO reqDTO) {
        return Result.ok(testCaseModuleService.createModule(projectId, reqDTO));
    }

    @PutMapping("/{id}")
    public Result<TestCaseModuleTreeRespDTO> updateModule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id,
            @RequestBody @Valid TestCaseModuleUpdateReqDTO reqDTO) {
        return Result.ok(testCaseModuleService.updateModule(id, reqDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteModule(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String id) {
        testCaseModuleService.deleteModule(id);
        return Result.ok();
    }
}
