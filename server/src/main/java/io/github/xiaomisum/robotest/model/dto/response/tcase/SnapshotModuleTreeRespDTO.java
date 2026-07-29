package io.github.xiaomisum.robotest.model.dto.response.tcase;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 评审/计划模块快照树节点（两域结构一致，共用一个 DTO）
 */
@Data
public class SnapshotModuleTreeRespDTO {

    private UUID id;
    private UUID parentId;
    private String name;
    private String type;
    private Integer sortOrder;
    private List<SnapshotModuleTreeRespDTO> children;
}
