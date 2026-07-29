package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkspaceMapper extends BaseMapperX<Workspace> {

    default Workspace findByName(String name) {
        return selectOne(Workspace::getName, name);
    }

    default List<Workspace> listByIds(Collection<UUID> ids) {
        return selectList(new LambdaQueryWrapperX<Workspace>().in(Workspace::getId, ids));
    }

    default PageResult<Workspace> findPage(PageParam pageParam, String keyword, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<Workspace>()
                .likeIfPresent(Workspace::getName, keyword)
                .eqIfPresent(Workspace::getStatus, status)
                .orderByDesc(Workspace::getCreatedAt));
    }

}
