package io.github.xiaomisum.robotest.service.domain.file;

import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 缺陷附件本地存量回填（文件管理详设 7.2）：逐行把本地磁盘文件迁入对象存储并补 file_resource_id。
 * 幂等（以 file_resource_id IS NULL 驱动）、不阻塞启动；本地缺文件跳过，存储类失败本轮中止待下次启动重试。
 */
@Component
public class FileResourceBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FileResourceBackfillRunner.class);

    @Resource
    private BugAttachmentMapper bugAttachmentMapper;
    @Resource
    private FileResourceService fileResourceService;

    @Value("${robotest.upload.dir:./uploads/bug}")
    private String uploadDir;

    @Override
    public void run(ApplicationArguments args) {
        List<BugAttachment> pending = loadPending();
        if (pending.isEmpty()) {
            return;
        }
        int migrated = 0;
        int skipped = 0;
        for (BugAttachment attachment : pending) {
            Path path = Paths.get(uploadDir).resolve(attachment.getStoragePath());
            if (Files.notExists(path)) {
                log.warn("附件存量回填跳过（本地文件不存在）：attachmentId={} path={}", attachment.getId(), path);
                skipped++;
                continue;
            }
            try {
                long size = Files.size(path);
                FileResource stored = fileResourceService.store(
                        attachment.getFileName(), attachment.getContentType(), size,
                        () -> {
                            try {
                                return Files.newInputStream(path);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        },
                        attachment.getUploaderId());
                bugAttachmentMapper.update(null, new LambdaUpdateWrapperX<BugAttachment>()
                        .eq(BugAttachment::getId, attachment.getId())
                        .set(BugAttachment::getFileResourceId, stored.getId()));
                migrated++;
            } catch (ServiceException e) {
                // 存储不可用或校验失败：本轮中止，回填期下载/删除仍走本地兼容路径（详设 7.2）
                log.warn("附件存量回填中断：{}（attachmentId={}）", e.getMessage(), attachment.getId());
                return;
            } catch (Exception e) {
                log.warn("附件存量回填单行失败（attachmentId={}）", attachment.getId(), e);
                skipped++;
            }
        }
        log.info("附件存量回填完成：迁移 {} 条，跳过 {} 条", migrated, skipped);
    }

    private List<BugAttachment> loadPending() {
        try {
            return bugAttachmentMapper.selectList(new LambdaQueryWrapperX<BugAttachment>()
                    .isNull(BugAttachment::getFileResourceId)
                    .isNotNull(BugAttachment::getStoragePath)
                    .last("LIMIT 500"));
        } catch (Exception e) {
            // DDL 未执行（file_resource_id 列不存在）等场景：跳过本轮，不阻塞启动
            log.warn("附件存量回填跳过（待办查询失败）：{}", e.getMessage());
            return List.of();
        }
    }
}
