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

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_environment", autoResultMap = true)
public class ApiEnvironment extends BaseUuidDO<ApiEnvironment> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String name;
    private String description;
    /** 环境归属范围：project（V1.2 全部）/ global（全局级预留扩展） */
    private String scope;
    private Boolean isDefault;
    private Integer sortOrder;
    /** HTTP 配置聚合（JSONB）：[{name, refName, baseUrl, headers, isDefault}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> httpConfigs;
    /** 环境变量聚合（JSONB）：[{name, value, description, sourceStepId, sourceReportId}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> variables;
    /** 数据源聚合（JSONB）：[{name, refName, driver, url, connectionProperties, maxPoolSize, isDefault}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> dataSources;
    /** 环境处理器聚合（JSONB）：[{processorType, name, enabled, sortOrder, config}] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> processors;
}
