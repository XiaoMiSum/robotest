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
 * 接口公共步骤（接口管理详细设计 2.1.3），供场景选择添加
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_interface_step", autoResultMap = true)
public class ApiInterfaceStep extends BaseUuidDO<ApiInterfaceStep> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceId;
    private String name;
    /** http / jdbc，V1.2 仅 http */
    private String stepType;
    private Integer sortOrder;
    private Boolean enabled;
    /** http: method/path/headers/params/body/auth */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> requestConfig;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> processors;
    /** 梯队三前透传存储不渲染 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> validators;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> extractors;
}
