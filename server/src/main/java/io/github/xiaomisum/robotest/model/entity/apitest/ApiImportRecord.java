package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 导入记录（API 测试基础设施详细设计 2.1.6），每次导入的结果留痕
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_import_record", autoResultMap = true)
public class ApiImportRecord extends BaseUuidDO<ApiImportRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** file_swagger / file_postman / file_har / file_jmeter / url_swagger */
    private String importType;
    private String sourceName;
    /** success / partial / failed */
    private String status;
    /** {created, updated, failed, skipped} */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> summary;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> errorDetails;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID repositoryId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;
}
