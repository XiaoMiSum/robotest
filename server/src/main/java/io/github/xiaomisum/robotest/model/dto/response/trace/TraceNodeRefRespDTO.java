package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.util.UUID;

/**
 * 追溯节点引用（详设 3.4 / 3.10 的 source / target / target 结构）。
 * title 由矩阵服务按 type 分发批量回填（逻辑外键回填），解析不到时按占位处理。
 */
@Data
public class TraceNodeRefRespDTO {

    /** 节点类型：requirement / module / mindmap_document / test_case / test_review / test_plan */
    private String type;

    private UUID id;

    private String title;

    /** 目标内容当前版本标识（详设 3.4），无版本概念的节点类型为 null */
    private String version;

    public static TraceNodeRefRespDTO of(String type, UUID id, String title) {
        TraceNodeRefRespDTO ref = new TraceNodeRefRespDTO();
        ref.setType(type);
        ref.setId(id);
        ref.setTitle(title);
        return ref;
    }
}
