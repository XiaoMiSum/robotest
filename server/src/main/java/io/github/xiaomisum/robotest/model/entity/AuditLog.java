package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "audit_log", autoResultMap = true)
public class AuditLog extends BaseUuidDO<AuditLog> {

    private UUID operatorId;
    private String operatorName;
    private String operation;
    private String entityType;
    private UUID entityId;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = Jackson3TypeHandler.class)
    private java.util.Map<String, Object> changes;

    private String requestIp;
}
