package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface RequirementPoolItemMapper extends BaseMapperX<RequirementPoolItem> {

    default PageResult<RequirementPoolItem> findPage(PageParam pageParam, UUID projectId, String keyword) {
        LambdaQueryWrapperX<RequirementPoolItem> wrapper = new LambdaQueryWrapperX<RequirementPoolItem>()
                .eq(RequirementPoolItem::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) {
            // 项目内条目量级小，标题模糊即可，不建全文索引（详细设计 2.1.1）
            wrapper.like(RequirementPoolItem::getTitle, keyword);
        }
        wrapper.orderByDesc(RequirementPoolItem::getUpdatedAt);
        return selectPage(pageParam, wrapper);
    }
}
