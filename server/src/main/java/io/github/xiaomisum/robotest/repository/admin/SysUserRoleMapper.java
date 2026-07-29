package io.github.xiaomisum.robotest.repository.admin;

import io.github.xiaomisum.robotest.model.entity.SysUserRole;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SysUserRoleMapper extends BaseMapperX<SysUserRole> {

    default List<SysUserRole> listByUserId(UUID userId) {
        return selectList(new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, userId));
    }

    default List<SysUserRole> listByRoleId(UUID roleId) {
        return selectList(new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
    }

    default List<SysUserRole> listByRoleIds(Collection<UUID> roleIds) {
        return selectList(new LambdaQueryWrapperX<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
    }

    default void deleteByUserId(UUID userId) {
        delete(new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, userId));
    }
}
