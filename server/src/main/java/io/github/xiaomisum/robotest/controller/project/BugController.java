package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.BugAssignReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugAttachmentDownloadDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugAttachmentRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugStatisticsRespDTO;
import io.github.xiaomisum.robotest.service.project.BugAttachmentService;
import io.github.xiaomisum.robotest.service.project.BugService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project/bugs")
public class BugController {

    @Resource
    private BugService bugService;
    @Resource
    private BugAttachmentService bugAttachmentService;

    @GetMapping
    public Result<PageResult<BugListRespDTO>> getBugPage(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String bugType,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(bugService.getBugPage(projectId, status, severity, priority,
                bugType, assigneeId, keyword, pageNo, pageSize));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<String> createBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid BugCreateReqDTO reqDTO) {
        return Result.ok(bugService.createBug(projectId, loginUser.getId(), reqDTO));
    }

    @GetMapping("/{id}")
    public Result<BugDetailRespDTO> getBugDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(bugService.getBugDetail(id));
    }

    @PutMapping("/{id}")
    public Result<Void> updateBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid BugUpdateReqDTO reqDTO) {
        bugService.updateBug(id, loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @GetMapping("/{id}/logs")
    public Result<List<BugLogRespDTO>> getBugLogs(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(bugService.getBugLogs(id));
    }

    @PatchMapping("/{id}/status")
    public Result<Void> changeBugStatus(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid BugStatusChangeReqDTO reqDTO) {
        bugService.changeBugStatus(id, loginUser.getId(), reqDTO);
        return Result.ok();
    }

    @PatchMapping("/{id}/confirm")
    public Result<Void> confirmBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        bugService.confirmBug(id, loginUser.getId());
        return Result.ok();
    }

    @PutMapping("/{id}/assign")
    public Result<Void> assignBug(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid BugAssignReqDTO reqDTO) {
        bugService.assignBug(id, loginUser.getId(),
                reqDTO.getAssigneeId());
        return Result.ok();
    }

    @GetMapping("/statistics")
    public Result<BugStatisticsRespDTO> getBugStatistics(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId) {
        return Result.ok(bugService.getBugStatistics(projectId));
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<BugAttachmentRespDTO> uploadAttachment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(bugAttachmentService.uploadAttachment(id, loginUser.getId(), file));
    }

    @GetMapping("/{id}/attachments")
    public Result<List<BugAttachmentRespDTO>> getAttachments(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(bugAttachmentService.getAttachments(id));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadAttachment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID attachmentId) {
        BugAttachmentDownloadDTO dto = bugAttachmentService.downloadAttachment(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dto.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dto.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .body(dto.getContent());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public Result<Void> deleteAttachment(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID attachmentId) {
        bugAttachmentService.deleteAttachment(attachmentId, loginUser.getId());
        return Result.ok();
    }
}
