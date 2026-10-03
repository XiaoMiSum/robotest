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
 * AI 任务（统一任务收口，详设 2.6）：全部 AI 执行的单一入口，result 为产物明细单一事实源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task", autoResultMap = true)
public class AiTask extends BaseUuidDO<AiTask> {

    /** 任务类型（详设 3.6.1 枚举） */
    private String type;

    /** pending / running / succeeded / failed / cancelled */
    private String status;

    private Integer progress;

    /** 当前阶段（如「生成脑图文档」），进度页展示用 */
    private String phase;

    /** 项目内任务的隔离归属（null = 不限项目的个人任务） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 执行作用域，RAG 限权过滤依据 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;

    /** 发起人：「我的任务」、完成通知与重试的归属 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID submittedBy;

    /** 实际使用的提示词场景（等于 type），用量按场景归因 */
    private String promptScene;

    /** 实际调用的模型，失败重试复用同模型 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID modelId;

    /** 任务输入：源引用 + 参数（存引用不复制全文） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> input;

    /** 产物明细（单一事实源），确认/驳回只指回这里 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> result;

    private Integer tokensIn;

    private Integer tokensOut;

    /** 失败业务错误码（10 位） */
    private Integer errorCode;

    private String errorMsg;

    /** 重试来源任务（3.6.4，原任务保留） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID retryOfTaskId;
}
