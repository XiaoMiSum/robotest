package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentDownloadRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface BugAttachmentService {

    /**
     * 上传缺陷附件（缺陷已关闭时拒绝）
     *
     * @param projectId 活动项目 ID（X-Active-Project 头，须与资源归属一致）
     * @param bugId  缺陷 ID
     * @param userId 上传用户 ID
     * @param file   上传文件
     * @return 附件信息
     */
    BugAttachmentRespDTO uploadAttachment(UUID projectId, UUID bugId, UUID userId, MultipartFile file);

    /**
     * 获取缺陷附件列表
     *
     * @param projectId 活动项目 ID（X-Active-Project 头，须与资源归属一致）
     * @param bugId  缺陷 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @return 按上传时间倒序的附件列表
     */
    List<BugAttachmentRespDTO> getAttachments(UUID projectId, UUID bugId, UUID userId);

    /**
     * 下载附件内容
     *
     * @param projectId 活动项目 ID（X-Active-Project 头，须与资源归属一致）
     * @param attachmentId 附件 ID
     * @param userId       当前用户 ID（用于项目归属校验）
     * @return 文件名、类型与内容
     */
    BugAttachmentDownloadRespDTO downloadAttachment(UUID projectId, UUID attachmentId, UUID userId);

    /**
     * 读取附件字节（AI 截图入模等内部消费，与下载同源）：不做成员与缺陷状态校验，
     * 归属由调用方保证；已回填读文件管理模块、未回填兼容本地历史文件。
     *
     * @param attachment 附件记录
     * @return 文件字节
     */
    byte[] readBytes(BugAttachment attachment);

    /**
     * 删除附件（逻辑删除，缺陷已关闭时拒绝）
     *
     * @param projectId 活动项目 ID（X-Active-Project 头，须与资源归属一致）
     * @param attachmentId 附件 ID
     * @param userId       操作用户 ID
     */
    void deleteAttachment(UUID projectId, UUID attachmentId, UUID userId);
}
