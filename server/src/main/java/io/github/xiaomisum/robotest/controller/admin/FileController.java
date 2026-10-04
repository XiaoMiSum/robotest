package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.file.FilePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileAccessUrlRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.file.FileResourceRespDTO;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 文件管理（文件管理详设 4.1）：泛化附件资源，不挂工作空间 / 项目上下文（C4 无上下文可传）。
 * 上传 / 下载 / 换签为登录态通用能力，列表 / 删除以权限点收口（file:view / file:delete）。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Resource
    private FileResourceService fileResourceService;

    // 上传 / 下载 / 换签为登录态通用能力（详设 5.1）；SEC-002 守卫要求管理包内显式声明鉴权
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<FileResourceRespDTO> upload(@AuthenticationPrincipal LoginUser loginUser,
                                              @RequestParam("file") MultipartFile file) {
        return Result.ok(fileResourceService.upload(file, loginUser.getId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('file:view')")
    public Result<PageResult<FileResourceRespDTO>> page(@Valid FilePageReqDTO query) {
        return Result.ok(fileResourceService.page(query));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        FileContent file = fileResourceService.readBytes(id);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(file.content().length)
                .body(file.content());
    }

    @GetMapping("/{id}/access-url")
    @PreAuthorize("isAuthenticated()")
    public Result<FileAccessUrlRespDTO> accessUrl(@PathVariable UUID id) {
        return Result.ok(fileResourceService.accessUrl(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('file:delete')")
    public Result<Void> delete(@PathVariable UUID id) {
        fileResourceService.delete(id);
        return Result.ok();
    }
}
