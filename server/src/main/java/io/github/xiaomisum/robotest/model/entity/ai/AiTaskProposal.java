package io.github.xiaomisum.robotest.model.entity.ai;

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
 * 任务提案（详设 2.6.4）：AI 执行的结构化产出，人工采纳的唯一入口；
 * 提案经采纳（ai_task_adoption）才落库，全部处置完毕后任务转 closed。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task_proposal", autoResultMap = true)
public class AiTaskProposal extends BaseUuidDO<AiTaskProposal> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID taskId;

    /** 所属执行：提案键按执行命名空间，再次执行的同名键并存（uk 挂 execution_id） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID executionId;

    /** 提案在本次执行产出中的定位键 */
    private String proposalKey;

    /** 提案类型（kind 取值由各分册定义，如 module_suggestion） */
    private String kind;

    /** 树形提案的父提案键 */
    private String parentKey;

    private String title;

    /** 结构化产物 + 证据链（结构由各分册定义） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> content;

    /** pending（待采纳）/ not_applicable（只读建议，无确认动作）（详设 2.6.4） */
    private String confirmStatus;
}
