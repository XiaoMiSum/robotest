package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 快速调试执行请求（详细设计 3.1.1）
 */
@Data
public class ApiDebugExecuteReqDTO {

    /** 协议：V1.2 仅支持 http */
    private String protocol = "http";

    @NotBlank(message = "请求方法不能为空")
    private String method;

    @NotBlank(message = "URL 不能为空")
    @Size(max = 2000, message = "URL 长度不能超过 2000")
    private String url;

    /** 请求头 [{key, value, enabled}] */
    private List<Map<String, Object>> headers;

    @Valid
    private Body body;

    /** Query 参数 [{key, value, enabled}] */
    private List<Map<String, Object>> params;

    /** 前置处理器列表（Ryze 处理器元件结构） */
    private List<Map<String, Object>> processors;

    /** 响应超时（毫秒），空则用默认 30000 */
    private Integer timeoutMs;

    /** 执行引用的环境（相对 URL 拼接与变量来源），空则使用项目默认环境 */
    private UUID environmentId;

    @Data
    public static class Body {

        /** none / json / form / raw / binary */
        private String type = "none";

        /** 请求体内容（结构随 type） */
        private Object content;
    }
}
