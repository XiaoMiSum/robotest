package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

/** 环境列表项（GET /api/project/environments，默认环境置顶） */
@Data
public class ApiEnvironmentListItemRespDTO {

    private String id;
    private String name;
    private String description;
    private Boolean isDefault;
    private Integer sortOrder;
    private Long variableCount;
    private Long dataSourceCount;
    private Long processorCount;
}
