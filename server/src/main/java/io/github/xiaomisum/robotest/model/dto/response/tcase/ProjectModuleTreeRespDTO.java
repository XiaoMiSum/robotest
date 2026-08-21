package io.github.xiaomisum.robotest.model.dto.response.tcase;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ProjectModuleTreeRespDTO {

    private UUID id;
    private UUID parentId;
    /** 节点类型：directory = 目录，document = 文档 */
    private String type;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    // 资产统计：目录下直接挂载的文档/用例数量
    private Integer count;
    private List<ProjectModuleTreeRespDTO> children;
}