package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 追溯边（详设 3.4 列表项与 3.5 / 3.6 单条结构同构）。
 */
@Data
public class TraceEdgeRespDTO {

    private UUID edgeId;

    private String edgeType;

    private TraceNodeRefRespDTO source;

    private TraceNodeRefRespDTO target;

    private String targetVersion;

    private String status;

    private String establishedBy;

    private UUID confirmedBy;

    private LocalDateTime confirmedAt;
}
