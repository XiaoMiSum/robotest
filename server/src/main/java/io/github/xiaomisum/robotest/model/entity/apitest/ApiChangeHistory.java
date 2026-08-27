package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

/**
 * 变更历史（API测试基础设施详细设计 2.1.2），只读追溯
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_change_history", autoResultMap = true)
public class ApiChangeHistory extends BaseUuidDO<ApiChangeHistory> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** interface / scene */
    private String targetType;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID targetId;
    /** 从 1 递增，同一对象内唯一 */
    private Integer version;
    /** create / update / import / copy */
    private String changeType;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> contentDiff;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;

}
