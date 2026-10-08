package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugAnalysisQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugMetricsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugTrendsRespDTO;
import io.github.xiaomisum.robotest.service.domain.bug.BugAnalysisService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

/**
 * 缺陷分析（详设 3.1）：查询与检测入口，统计实时计算；业务逻辑在 Service（C2）。
 */
@RestController
@RequestMapping("/api/project/bugs")
public class BugAnalysisController {

    @Resource
    private BugAnalysisService bugAnalysisService;

    @GetMapping("/analysis/trends")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<BugTrendsRespDTO> getTrends(@AuthenticationPrincipal LoginUser loginUser,
            BugAnalysisQueryReqDTO query) {
        return Result.ok(bugAnalysisService.trends(query, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @GetMapping("/analysis/metrics")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<BugMetricsRespDTO> getMetrics(@AuthenticationPrincipal LoginUser loginUser,
            BugAnalysisQueryReqDTO query) {
        return Result.ok(bugAnalysisService.metrics(query, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @PostMapping("/duplicates/check")
    @PreAuthorize("hasAuthority('bug:view')")
    public Result<BugDuplicateCheckRespDTO> checkDuplicates(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody BugDuplicateCheckReqDTO reqDTO) {
        return Result.ok(bugAnalysisService.checkDuplicates(reqDTO,
                loginUser.getActiveProjectId(), loginUser.getId()));
    }
}
