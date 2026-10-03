package io.github.xiaomisum.robotest.model.entity.requirement;

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
 * 需求变更记录（详设 2.3）：change_type 只有 title / description / module 触发影响标记，
 * attribute / status 供"哪类变更触发了什么"的回查。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "requirement_change_log", autoResultMap = true)
public class RequirementChangeLog extends BaseUuidDO<RequirementChangeLog> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID requirementId;

    /** 操作人（系统触发时为发起人） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID operatorId;

    private String changeType;

    /** 变更前字段级摘要（原值） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> beforeSummary;

    /** 变更后字段级摘要（新值） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> afterSummary;
}
