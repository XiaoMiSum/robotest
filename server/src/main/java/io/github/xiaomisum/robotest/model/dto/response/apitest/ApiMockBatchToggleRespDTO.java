package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

/** Mock 批量启停结果（Mock服务详细设计 3.1.11） */
@Data
public class ApiMockBatchToggleRespDTO {

    private Integer updatedCount;

    public ApiMockBatchToggleRespDTO() {
    }

    public ApiMockBatchToggleRespDTO(Integer updatedCount) {
        this.updatedCount = updatedCount;
    }

}
