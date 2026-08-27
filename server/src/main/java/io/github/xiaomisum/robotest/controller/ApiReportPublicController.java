package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicReportRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/**
 * 报告分享免登录访问（基础设施详细设计 3.4.4）
 * 路径在 permit-all-urls 白名单内；鉴权完全依赖一次性 token，不接入 @PreAuthorize
 */
@RestController
@RequestMapping("/api/public/api-reports")
public class ApiReportPublicController {

    @Resource
    private ApiReportService reportService;

    @GetMapping("/{id}")
    public Result<ApiPublicReportRespDTO> access(
            @PathVariable UUID id,
            @RequestParam("token") String token) {
        return Result.ok(reportService.publicAccess(id, token));
    }

}
