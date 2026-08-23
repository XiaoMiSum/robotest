package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 3.1.8 HTTP 连接测试结果：网络层失败也以 success=false 结构化返回（HTTP 200） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiHttpTestRespDTO {

    private Boolean success;

    private String message;

    private Integer statusCode;

    private Long durationMs;
}
