package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 任务响应（详设 3.6.2 / 3.6.3）：列表项不含 result / artifacts；详情与同步等待响应附产物清单摘要。
 */
@Data
public class AiTaskRespDTO {

    private UUID taskId;
    private String type;
    private String status;
    private Integer progress;
    private String phase;
    private UUID submittedBy;
    private UUID retryOfTaskId;
    private Integer tokensIn;
    private Integer tokensOut;
    private LocalDateTime createdAt;
    /** 失败时 { code, msg }，其余状态为 null */
    private TaskError error;
    /** 仅同步等待完成（3.6.2）携带产物明细 */
    private Map<String, Object> result;
    /** status = succeeded 时的产物清单摘要（3.6.3） */
    private List<ArtifactSummary> artifacts;

    @Data
    public static class TaskError {

        private Integer code;
        private String msg;

        public TaskError() {
        }

        public TaskError(Integer code, String msg) {
            this.code = code;
            this.msg = msg;
        }
    }

    @Data
    public static class ArtifactSummary {

        private String key;
        private String kind;
        private String title;
        private String parentKey;
        /** pending / adopted / adopted_edited / rejected */
        private String confirmStatus;
    }
}
