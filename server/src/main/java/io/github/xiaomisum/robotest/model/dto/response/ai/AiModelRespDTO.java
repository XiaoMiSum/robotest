package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 模型配置行（GET /api/ai/models，详设 3.3；密钥永不回显）。
 */
@Data
public class AiModelRespDTO {

    private UUID id;

    private String name;

    private String provider;

    private String baseUrl;

    private String modelName;

    private List<String> capabilities;

    private Integer priority;

    private Boolean enabled;

    /** 输入侧每百万 token 单价 */
    private BigDecimal inputPrice;

    /** 输出侧每百万 token 单价 */
    private BigDecimal outputPrice;

    /** 密钥是否已配置（永不返回密文） */
    private Boolean keyConfigured;

    /** 最近连通性测试（无记录为 null） */
    private LastTest lastTest;

    /**
     * 最近连通性测试结果（失败 msg 供悬浮原因展示，交互 2.4）。
     */
    @Data
    public static class LastTest {

        private Boolean success;

        private Integer latencyMs;

        /** 失败摘要（成功为 null） */
        private String msg;

        private LocalDateTime at;
    }
}
