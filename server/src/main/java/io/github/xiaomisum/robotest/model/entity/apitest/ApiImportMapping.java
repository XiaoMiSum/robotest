package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 导入映射（接口管理详细设计 2.1.5），支撑增量导入去重与覆盖
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_import_mapping")
public class ApiImportMapping extends BaseUuidDO<ApiImportMapping> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID importRecordId;
    /** swagger_operation / postman_item / har_entry / jmeter_sampler */
    private String sourceType;
    private String sourceId;
    private String sourceName;
    /** interface / scene */
    private String targetType;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID targetId;
    /** created / updated / skipped */
    private String action;
}
