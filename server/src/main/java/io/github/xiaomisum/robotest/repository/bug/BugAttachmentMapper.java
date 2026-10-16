package io.github.xiaomisum.robotest.repository.bug;

import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface BugAttachmentMapper extends BaseMapperX<BugAttachment> {

    default List<BugAttachment> listByBugId(UUID bugId) {
        return selectList(new LambdaQueryWrapperX<BugAttachment>().eq(BugAttachment::getBugId, bugId));
    }

    /** 按缺陷集合取附件（AI 批量截图收集与能力派生），上传时间倒序保证取样确定性 */
    default List<BugAttachment> listByBugIds(List<UUID> bugIds) {
        return selectList(new LambdaQueryWrapperX<BugAttachment>()
                .in(BugAttachment::getBugId, bugIds)
                .orderByDesc(BugAttachment::getCreatedAt));
    }
}
