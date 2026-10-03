package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 产物确认（POST /api/ai/tasks/{taskId}/artifacts/confirm，详设 3.6.5）：批量、逐项动作。
 */
@Data
public class AiTaskConfirmReqDTO {

    @NotEmpty(message = "确认项不能为空")
    @Size(max = 200, message = "单次最多确认 200 项")
    private List<ConfirmItemReqDTO> items;

    /** 落库目标位置提示（生成链场景），项目经 X-Active-Project 头传递 */
    private TargetReqDTO target;

    @Data
    public static class ConfirmItemReqDTO {

        private String key;

        /** adopted / adopted_edited / rejected（否则逐项回执 1000018115） */
        private String action;

        /** adopted_edited 的编辑后内容 */
        private Map<String, Object> content;

        /** 驳回说明 */
        @Size(max = 500, message = "驳回说明不能超过 500 字符")
        private String note;
    }

    @Data
    public static class TargetReqDTO {

        private UUID moduleId;

        /** 位置策略（如 sibling / child），由承接服务解释 */
        private String position;
    }
}
