package io.github.xiaomisum.robotest.model.entity.trace;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 覆盖结论（追溯矩阵详设 2.3）：矩阵单元格的业务状态（需求 × 用例集合的覆盖质量结论），
 * 每需求至多一条；reviewed_by 非空即人工判定优先，AI 分析不得覆盖。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "trace_coverage_result", autoResultMap = true)
public class TraceCoverageResult extends BaseUuidDO<TraceCoverageResult> {

    /** 隔离边界 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 需求条目 ID（逻辑外键） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID requirementId;

    /** covered / partial / uncovered，见 Constants.TraceCoverageStatus */
    private String coverageStatus;

    /** 判定依据：命中的用例集合、缺口说明、AI 理由摘要 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> evidence;

    /** 来源覆盖分析任务 ID（逻辑外键 → ai_task） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID analyzedTaskId;

    private LocalDateTime aiAnalyzedAt;

    /** 人工复核修正人；非空即人工判定优先 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID reviewedBy;

    private String reviewedNote;

    private LocalDateTime reviewedAt;
}
