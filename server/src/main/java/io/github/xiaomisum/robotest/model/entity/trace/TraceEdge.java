package io.github.xiaomisum.robotest.model.entity.trace;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 追溯边（追溯矩阵详设 2.2）：节点间关联的结构事实。
 * 断开以 status = detached 承载而非复用 is_deleted——唯一约束对有效行始终生效，
 * 从而保证人工断开后 AI 不得自动重建（2.2 设计说明 3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trace_edge")
public class TraceEdge extends BaseUuidDO<TraceEdge> {

    /** 隔离边界，矩阵 / 链路 / 影响查询强制过滤 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** derivation（派生边）/ case_snapshot（快照引用边），见 Constants.TraceEdgeType */
    private String edgeType;

    private String sourceType;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceId;

    private String targetType;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID targetId;

    /** 引用时目标内容的版本标识，与当前版本不一致则边转 stale */
    private String targetVersion;

    /** ai_created / confirmed / conflict / stale / detached，见 Constants.TraceEdgeStatus */
    private String status;

    /** ai / manual，见 Constants.TraceEstablishedBy */
    private String establishedBy;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID confirmedBy;

    private LocalDateTime confirmedAt;

    /** 影响处置标记：null 表示未纳入影响分析，见 Constants.TraceDisposition */
    private String disposition;

    /** 处置理由，no_impact 必填 */
    private String reason;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID disposedBy;
}
