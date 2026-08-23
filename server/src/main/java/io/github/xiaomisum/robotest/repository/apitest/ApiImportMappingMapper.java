package io.github.xiaomisum.robotest.repository.apitest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportMapping;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ApiImportMappingMapper extends BaseMapperX<ApiImportMapping> {

    /** 增量导入匹配：按源类型 + 源标识定位既有映射 */
    default ApiImportMapping selectBySource(UUID projectId, String sourceType, String sourceId) {
        return selectOne(new LambdaQueryWrapper<ApiImportMapping>()
                .eq(ApiImportMapping::getProjectId, projectId)
                .eq(ApiImportMapping::getSourceType, sourceType)
                .eq(ApiImportMapping::getSourceId, sourceId)
                .last("LIMIT 1"));
    }

    default List<ApiImportMapping> selectListByTargetIds(Collection<UUID> targetIds) {
        return selectList(new LambdaQueryWrapperX<ApiImportMapping>()
                .in(ApiImportMapping::getTargetId, targetIds));
    }
}
