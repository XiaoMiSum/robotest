package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

/**
 * 批量创建需求池条目响应（US-AI-019，3.1.7）：实际入库条数。
 */
@Data
public class RequirementBatchCreateRespDTO {

    private Integer count;

    public static RequirementBatchCreateRespDTO of(int count) {
        RequirementBatchCreateRespDTO dto = new RequirementBatchCreateRespDTO();
        dto.setCount(count);
        return dto;
    }
}
