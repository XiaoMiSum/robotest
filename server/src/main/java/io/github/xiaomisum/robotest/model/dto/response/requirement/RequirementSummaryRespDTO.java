package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.util.UUID;

/**
 * 需求摘要（需求选取器与文档关联需求回显用，不含描述全文）。
 */
@Data
public class RequirementSummaryRespDTO {

    private UUID id;
    private String code;
    private String title;
}
