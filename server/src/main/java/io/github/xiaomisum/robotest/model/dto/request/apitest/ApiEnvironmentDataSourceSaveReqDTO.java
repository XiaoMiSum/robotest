package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 数据源独立新增/编辑（3.1.12）：按数据源粒度即时保存，连接池随编辑释放重建。
 */
@Data
public class ApiEnvironmentDataSourceSaveReqDTO {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotBlank(message = "引用名不能为空")
    private String refName;

    /** Redis 数据源免驱动，前端以 '-' 占位满足 driver 必填校验 */
    @NotBlank(message = "驱动不能为空")
    private String driver;

    private String url;

    private Map<String, Object> connectionProperties;

    private Integer maxPoolSize;
}