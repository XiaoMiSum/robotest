package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 全局助手发送消息请求（详细设计 3.2）
 */
@Data
public class AiAssistantSendReqDTO {

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 页面上下文桥注入，可空 */
    private Map<String, Object> pageContext;

    /** 对话模型标识，可空（缺省/失效回退系统默认） */
    private String modelId;
}
