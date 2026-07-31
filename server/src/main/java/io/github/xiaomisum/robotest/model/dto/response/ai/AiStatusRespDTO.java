package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * AI 可用性状态（前端据此显隐全部 AI 入口）
 */
@Data
public class AiStatusRespDTO {

    private Boolean enabled;
    /** available / degraded / unavailable，enabled=false 时不返回 */
    private String semanticSearch;
    /** 已启用对话模型清单（仅 id/显示名/是否默认，脱敏），供交互式功能模型选择器渲染；enabled=false 时不返回 */
    private List<ChatModelView> chatModels;

    @Data
    public static class ChatModelView {
        private String id;
        private String name;
        private Boolean isDefault;

        public ChatModelView() {
        }

        public ChatModelView(String id, String name, Boolean isDefault) {
            this.id = id;
            this.name = name;
            this.isDefault = isDefault;
        }
    }
}
