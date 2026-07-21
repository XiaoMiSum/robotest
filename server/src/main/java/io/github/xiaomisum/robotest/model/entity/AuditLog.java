package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "audit_log", autoResultMap = true)
public class AuditLog {

    @TableId
    private Long id;

    private String operatorId;
    private String operatorName;
    private String operation;
    private String entityType;
    private String entityId;

    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private java.util.Map<String, Object> changes;

    private String requestIp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
