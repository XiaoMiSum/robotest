package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.util.UUID;

/**
 * 需求摘要（用例关联需求、计划/评审关联需求回显）。
 */
@Data
public class RequirementRefRespDTO {

    private UUID id;
    private String code;
    private String title;
}
