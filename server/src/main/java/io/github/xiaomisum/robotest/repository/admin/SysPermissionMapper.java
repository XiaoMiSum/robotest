package io.github.xiaomisum.robotest.repository.admin;

import io.github.xiaomisum.robotest.model.entity.SysPermission;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

import java.util.List;

public interface SysPermissionMapper extends BaseMapperX<SysPermission> {

    default List<SysPermission> listAll() {
        return selectList(null);
    }
}
