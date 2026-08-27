package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

/** 场景执行触发响应（测试场景详细设计 3.6.1） */
@Data
@Builder
public class ApiExecutionStartRespDTO {

    private String executionId;

    /** pending */
    private String status;

}
