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
}
