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
 * 需求拆解记录（详设 2.4）：拆分建议明细存任务产物侧，本表只记状态与采纳结果（单一事实源）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "requirement_split_record", autoResultMap = true)
public class RequirementSplitRecord extends BaseUuidDO<RequirementSplitRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 拆解来源：document（导入文档）/ requirement（条目内 AI 拆分） */
    private String sourceType;

    /** 导入的原始文档附件（来源为 document 时） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceFileId;

    /** 原条目标识（来源为 requirement 时；采纳后原条目归档，靠此保留关联） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceRequirementId;

    /** 关联 AI 任务（逻辑外键 → ai_task） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID aiTaskId;

    /** pending（建议待确认）/ adopted（采纳完成）/ rejected（全部驳回）；不设字段初值，避免更新载体误写状态 */
    private String status;

    /** 采纳结果：逐条产物动作与生成的新需求 ID 列表、操作人 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> adoptResult;
}
