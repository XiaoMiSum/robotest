package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO.HeaderItem;

/**
 * HTTP 配置免保存连接测试（详细设计 3.1.8）：按当前编辑值试连，
 * 未落库或已修改未保存的配置无需先保存即可验证。
 */
@Data
public class ApiHttpConfigTestReqDTO {

    @NotBlank(message = "Base URL 不能为空")
    @Size(max = 2000, message = "Base URL 不能超过 2000 字符")
    private String baseUrl;

    @Size(max = 100, message = "引用名称不能超过 100 字符")
    private String refName;

    private List<HeaderItem> headers;
}
