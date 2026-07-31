package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.util.UUID;

/**
 * 需求池条目摘要（文档关联查询与条目选取器共用的轻量视图）。
 */
@Data
public class RequirementSummaryRespDTO {

    private UUID id;
    private String title;
}
