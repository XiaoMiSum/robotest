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
 * AI 任务（统一任务收口，总册 2.6.1）：全部 AI 执行的单一入口。
 * 任务态 running → proposals_ready → closed / aborted；执行细节与提案在各自表，
 * 任务行只保留输入、汇总与终止原因（S3 重建后无 progress / phase / result 列）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task", autoResultMap = true)
public class AiTask extends BaseUuidDO<AiTask> {

    /** 任务类型（总册 3.6.1 枚举） */
    private String type;

    /** running / proposals_ready / closed / aborted（running 期间执行可挂起 waiting_confirmation） */
    private String status;

    /** 项目内任务的隔离归属（null = 不限项目的个人任务） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 执行作用域，RAG 限权过滤依据 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;

    /** 发起人：「我的任务」、完成通知与再次执行的归属 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID submittedBy;

    /** 任务输入：源引用 + 参数（存引用不复制全文） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> input;

    /** 文档级元数据（如 requirement_import 的 documentMeta），供确认面板预填 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> resultMeta;

    /** 任务级入向 token 汇总（跨全部执行累加） */
    private Integer tokensIn;

    /** 任务级出向 token 汇总（跨全部执行累加） */
    private Integer tokensOut;

    /** 终止原因错误码（10 位；用户中止 / 预算耗尽 / 执行错误 / 总开关关闭） */
    private Integer terminateCode;

    /** 终止原因摘要 */
    private String terminateMsg;
}
