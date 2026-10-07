package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.util.List;

/**
 * 确认执行（POST /api/ai/conversations/{id}/messages/{id}/execute，详设 3.7）：
 * 请求体可选，不带 retryIndexes 即首次执行，语义校验（1000001001）在服务层。
 */
@Data
public class AiAssistantExecuteReqDTO {

    /** 上次回执 results 中失败项的数组下标；省略或空表示首次执行 */
    private List<Integer> retryIndexes;
}
