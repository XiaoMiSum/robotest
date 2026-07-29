package io.github.xiaomisum.robotest.repository.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SysUserMapper extends BaseMapperX<SysUser> {

    default SysUser findByUsername(String username) {
        return selectOne(SysUser::getUsername, username);
    }

    default SysUser findByEmail(String email) {
        return selectOne(SysUser::getEmail, email);
    }

    default PageResult<SysUser> findPage(String keyword, String status, List<UUID> filteredUserIds,
                                         Integer pageNo, Integer pageSize) {
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getEmail, keyword)
                    .or().like(SysUser::getName, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (filteredUserIds != null && !filteredUserIds.isEmpty()) {
            wrapper.in(SysUser::getId, filteredUserIds);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        return selectPage(new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, wrapper);
    }

    default List<SysUser> listActiveByKeyword(String keyword) {
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<SysUser>()
                .eq(SysUser::getStatus, Constants.Status.ACTIVE);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getName, keyword)
                    .or().like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getEmail, keyword);
        }
        wrapper.orderByAsc(SysUser::getName);
        return selectList(wrapper);
    }

    default List<SysUser> listByIds(Collection<UUID> ids) {
        return selectList(new LambdaQueryWrapperX<SysUser>().in(SysUser::getId, ids));
    }

    default List<SysUser> listByKeyword(String keyword) {
        LambdaQueryWrapperX<SysUser> wrapper = new LambdaQueryWrapperX<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getEmail, keyword)
                    .or().like(SysUser::getName, keyword);
        }
        wrapper.orderByAsc(SysUser::getName);
        return selectList(wrapper);
    }
}
