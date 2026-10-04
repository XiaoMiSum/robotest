package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 链路视图（详设 3.3）：节点与边分离，前端按边渲染连线。
 */
@Data
public class TraceChainRespDTO {

    private TraceNodeRefRespDTO root;

    private List<ChainNode> nodes = List.of();

    private List<ChainEdge> edges = List.of();

    /** 超出深度（6 层）或节点数（2000）上限 */
    private boolean hasMore;

    @Data
    public static class ChainNode {
        private UUID id;
        private String type;
        private String title;
        /** 目标内容版本标识，无版本概念的节点类型为 null */
        private String version;
        /** 相对起点的层数，起点为 0 */
        private int level;
    }

    @Data
    public static class ChainEdge {
        private UUID edgeId;
        private UUID sourceId;
        private UUID targetId;
        private String edgeType;
        private String status;
        private String targetVersion;
        /** targetVersion 与当前版本是否一致；目标无版本概念时为 null 不参与判断 */
        private Boolean versionMatched;
    }
}
