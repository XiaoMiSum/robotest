package io.github.xiaomisum.robotest.repository.admin;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SysRoleMapper extends BaseMapperX<SysRole> {

    default List<SysRole> listByIds(Collection<UUID> ids) {
        return selectList(new LambdaQueryWrapperX<SysRole>().in(SysRole::getId, ids));
    }
}
