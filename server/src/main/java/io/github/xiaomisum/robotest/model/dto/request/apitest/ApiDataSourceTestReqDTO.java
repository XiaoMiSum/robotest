package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 数据源免保存连接测试（详细设计 3.1.7）：按表单当前值试连，
 * 未落库或已修改未保存的数据源无需先保存即可验证。
 */
@Data
public class ApiDataSourceTestReqDTO {

    /** Redis 数据源免驱动，允许空串 */
    private String driver;

    @NotBlank(message = "URL 不能为空")
    private String url;

    private Map<String, Object> connectionProperties;
}