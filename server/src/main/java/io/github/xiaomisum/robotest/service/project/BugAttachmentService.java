package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentDownloadDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentRespDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface BugAttachmentService {

    /**
     * 上传缺陷附件（缺陷已关闭时拒绝）
     *
     * @param bugId  缺陷 ID
     * @param userId 上传用户 ID
     * @param file   上传文件
     * @return 附件信息
     */
    BugAttachmentRespDTO uploadAttachment(UUID bugId, UUID userId, MultipartFile file);

    /**
     * 获取缺陷附件列表
     *
     * @param bugId 缺陷 ID
     * @return 按上传时间倒序的附件列表
     */
    List<BugAttachmentRespDTO> getAttachments(UUID bugId);

    /**
     * 下载附件内容
     *
     * @param attachmentId 附件 ID
     * @return 文件名、类型与内容
     */
    BugAttachmentDownloadDTO downloadAttachment(UUID attachmentId);

    /**
     * 删除附件（逻辑删除，缺陷已关闭时拒绝）
     *
     * @param attachmentId 附件 ID
     * @param userId       操作用户 ID
     */
    void deleteAttachment(UUID attachmentId, UUID userId);
}
