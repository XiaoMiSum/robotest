package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 3.1.7 数据源连接测试结果：失败场景以 7403 业务异常返回，不使用此结构 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiDataSourceTestRespDTO {

    private Boolean success;

    private String message;

    /** 连接成功时的数据库版本信息，如 "PostgreSQL 18.2" */
    private String databaseVersion;
}
