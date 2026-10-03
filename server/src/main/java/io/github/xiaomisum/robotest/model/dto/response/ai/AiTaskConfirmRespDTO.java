package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 产物确认回执（详设 3.6.5）：整体 200、逐项成败，请求级错误才返回错误码。
 */
@Data
public class AiTaskConfirmRespDTO {

    private List<ItemResult> results;

    @Data
    public static class ItemResult {

        private String key;
        private String action;
        private Boolean success;
        /** 采纳落库生成的业务实体 ID（驳回为 null） */
        private UUID createdId;
        private Integer errorCode;
        private String errorMsg;

        public static ItemResult ok(String key, String action, UUID createdId) {
            ItemResult r = new ItemResult();
            r.setKey(key);
            r.setAction(action);
            r.setSuccess(true);
            r.setCreatedId(createdId);
            return r;
        }

        public static ItemResult fail(String key, String action, int errorCode, String errorMsg) {
            ItemResult r = new ItemResult();
            r.setKey(key);
            r.setAction(action);
            r.setSuccess(false);
            r.setErrorCode(errorCode);
            r.setErrorMsg(errorMsg);
            return r;
        }
    }
}
