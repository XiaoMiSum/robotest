package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 执行记录（API测试基础设施详细设计 2.1.3）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_execution_record", autoResultMap = true)
public class ApiExecutionRecord extends BaseUuidDO<ApiExecutionRecord> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    /** platform */
    private String executionMode;
    /** pending → running → success / failed / error / cancelled / timeout */
    private String status;
    /** manual / scheduled */
    private String triggerType;
    /** 报告来源：scene（场景页运行）/ schedule（定时任务含立即执行） */
    private String source;
    /** 执行完成后写入关联报告 ID */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reportId;
    private String errorMessage;
    private LocalDateTime executedAt;
    private Integer durationMs;

    // ========== GitLab 流水线执行批次（GitLab 集成详细设计 2.2 / 4.2） ==========
    /** 来源仓库（api_git_repo.id），仅 CI 引擎批次非空 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID repoId;
    /** GitLab 流水线编号（iid），批次票据与流水线的关联键 */
    private Long pipelineIid;
    private String pipelineUrl;
    /** 外部执行批次票据（UUID 36 字符），同批次内所有记录相同 */
    private String batchId;
    /** 源侧引用（类全限定名#方法名），CI 侧生成 TestNG 方法过滤条件的依据 */
    private String sourceRef;
    /** 冻结时的默认分支 HEAD 提交号（批次快照的可追溯信息） */
    private String commitSha;
    /** 关联定时任务（api_scheduled_task.id），手动执行为空 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID scheduledTaskId;
    /** 批次结果摘要（期望 / 实际用例数等），终态回写 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> summary;

}
