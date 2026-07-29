package io.github.xiaomisum.robotest.repository.admin;

import io.github.xiaomisum.robotest.model.entity.admin.SysPermission;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;

public interface SysPermissionMapper extends BaseMapperX<SysPermission> {

    default List<SysPermission> listAll() {
        return selectList(null);
    }

    default List<SysPermission> findByScopeOrdered(String scope) {
        return selectList(new LambdaQueryWrapperX<SysPermission>()
                .eq(SysPermission::getScope, scope)
                .orderByAsc(SysPermission::getModule, SysPermission::getSortOrder));
    }
}
