package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface RequirementPoolItemMapper extends BaseMapperX<RequirementPoolItem> {

    default PageResult<RequirementPoolItem> findPage(PageParam pageParam, UUID projectId, String keyword,
            String status) {
        LambdaQueryWrapperX<RequirementPoolItem> wrapper = new LambdaQueryWrapperX<RequirementPoolItem>()
                .eq(RequirementPoolItem::getProjectId, projectId)
                // status 为空时返回全部（需求池管理页按状态筛选），非空时精确过滤
                .eqIfPresent(RequirementPoolItem::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            // 项目内条目量级小，标题模糊即可，不建全文索引（详细设计 2.1.1）
            wrapper.like(RequirementPoolItem::getTitle, keyword);
        }
        wrapper.orderByDesc(RequirementPoolItem::getUpdatedAt);
        return selectPage(pageParam, wrapper);
    }
}
