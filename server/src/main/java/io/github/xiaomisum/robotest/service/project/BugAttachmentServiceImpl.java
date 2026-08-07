package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.BugConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentDownloadDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugLogMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BugAttachmentServiceImpl implements BugAttachmentService {

    // 单文件大小上限 10MB，与 spring.servlet.multipart 配置保持一致
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    @Resource
    private BugAttachmentMapper bugAttachmentMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private BugLogMapper bugLogMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Value("${robotest.upload.dir:./uploads/bug}")
    private String uploadDir;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BugAttachmentRespDTO uploadAttachment(UUID bugId, UUID userId, MultipartFile file) {
        Bug bug = validateBugOperable(bugId);
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);

        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ATTACHMENT_STORE_FAILED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ATTACHMENT_SIZE_EXCEEDED);
        }

        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "unnamed";
        // 落盘文件名使用随机 UUID，避免原始文件名注入路径
        String ext = extractExtension(originalName);
        String relativePath = bug.getId() + "/" + UUID.randomUUID() + ext;
        Path target = Paths.get(uploadDir).resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ATTACHMENT_STORE_FAILED);
        }

        BugAttachment attachment = new BugAttachment();
        attachment.setBugId(bugId);
        attachment.setFileName(originalName);
        attachment.setStoragePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        attachment.setUploaderId(userId);
        bugAttachmentMapper.insert(attachment);

        writeBugLog(bugId, userId, Constants.BugOperation.ATTACHMENT_UPLOAD,
                String.format("上传附件「%s」", originalName));

        return toAttachmentRespDTO(attachment);
    }

    @Override
    public List<BugAttachmentRespDTO> getAttachments(UUID bugId, UUID userId) {
        Bug bug = validateBugOperable(bugId);
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);
        List<BugAttachment> attachments = bugAttachmentMapper.listByBugId(bugId);
        return attachments.stream().map(this::toAttachmentRespDTO).collect(Collectors.toList());
    }

    @Override
    public BugAttachmentDownloadDTO downloadAttachment(UUID attachmentId, UUID userId) {
        BugAttachment attachment = bugAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ATTACHMENT_NOT_FOUND);
        }
        Bug bug = bugMapper.selectById(attachment.getBugId());
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);

        Path path = Paths.get(uploadDir).resolve(attachment.getStoragePath());
        BugAttachmentDownloadDTO dto = new BugAttachmentDownloadDTO();
        dto.setFileName(attachment.getFileName());
        dto.setContentType(StringUtils.hasText(attachment.getContentType())
                ? attachment.getContentType() : "application/octet-stream");
        try {
            dto.setContent(Files.readAllBytes(path));
        } catch (IOException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ATTACHMENT_NOT_FOUND);
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(UUID attachmentId, UUID userId) {
        BugAttachment attachment = bugAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ATTACHMENT_NOT_FOUND);
        }
        Bug bug = validateBugOperable(attachment.getBugId());
        projectAccessGuard.requireProjectMember(bug.getProjectId(), userId);

        // 逻辑删除记录，磁盘文件保留以便审计追溯
        bugAttachmentMapper.deleteById(attachmentId);

        writeBugLog(attachment.getBugId(), userId, Constants.BugOperation.ATTACHMENT_DELETE,
                String.format("删除附件「%s」", attachment.getFileName()));
    }

    private Bug validateBugOperable(UUID bugId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_NOT_FOUND);
        }
        if (Constants.BugStatus.CLOSED.equals(bug.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_CLOSED_ATTACHMENT_FORBIDDEN);
        }
        return bug;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        // 后缀限制长度，防止异常文件名生成超长路径
        if (dotIndex < 0 || fileName.length() - dotIndex > 20) {
            return "";
        }
        return fileName.substring(dotIndex);
    }

    private BugAttachmentRespDTO toAttachmentRespDTO(BugAttachment attachment) {
        BugAttachmentRespDTO dto = BugConvertMapper.INSTANCE.toAttachmentRespDTO(attachment);
        if (attachment.getUploaderId() != null) {
            SysUser uploader = userMapper.selectById(attachment.getUploaderId());
            if (uploader != null) {
                dto.setUploaderName(uploader.getUsername());
            }
        }
        return dto;
    }

    private void writeBugLog(UUID bugId, UUID userId, String operationType, String content) {
        BugLog log = new BugLog();
        log.setBugId(bugId);
        log.setOperatorId(userId);
        log.setOperationType(operationType);
        log.setContent(content);
        bugLogMapper.insert(log);
    }
}
