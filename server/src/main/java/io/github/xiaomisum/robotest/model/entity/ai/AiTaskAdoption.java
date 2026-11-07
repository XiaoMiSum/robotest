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
 * 提案采纳记录（详设 2.7）：同一提案仅一条记录（uk_ai_task_adoption 幂等拦截 1000018113），
 * 审计口径为「AI 产出必经人工采纳」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task_adoption", autoResultMap = true)
public class AiTaskAdoption extends BaseUuidDO<AiTaskAdoption> {

    /** 落库目标项目（确认列表过滤），经请求头上报 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID taskId;

    /** 所采纳提案所属执行（与 ai_task_proposal.execution_id 对齐） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID executionId;

    /** 所采纳提案在 ai_task_proposal 中的定位键 */
    private String proposalKey;

    /** adopted / adopted_edited / rejected（详设 2.7） */
    private String action;

    /** 确认操作人（审计：AI 产出必经人工采纳） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID operatorId;

    /** 采纳落库后的目标实体引用（新建 ID、变更版本），反查这条数据来自哪个提案 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> adoptedRef;

    /** 驳回 / 编辑原因 */
    private String note;
}
