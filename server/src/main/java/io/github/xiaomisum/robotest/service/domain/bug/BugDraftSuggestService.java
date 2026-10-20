package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugDraftSuggestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 新建缺陷实时建议（详设 3.5）：基于标题与已填内容流式单跳模型调用（SSE），
 * 不产生任务、不落库，建议仅以徽标呈现、用户值优先。
 */
public interface BugDraftSuggestService {

    /**
     * 前置校验同步完成（项目成员 / 标题合法 1000018287 / 可用模型 1000018118），
     * 失败抛业务异常走 HTTP 错误非流；通过后返回事件流（progress / done / error + 15s 心跳）。
     */
    SseEmitter suggest(BugDraftSuggestReqDTO reqDTO, UUID projectId, UUID userId);

    /**
     * 可测核心：上下文装配 → 流式模型调用 → 清洗并经 sink 回写事件（在 @Async 线程执行）；
     * 装配 / 调用 / 清洗失败统一映射为 error 事件，done 之后无事件。
     */
    void stream(BugDraftSuggestReqDTO reqDTO, UUID projectId, UUID userId, AiModelConfig model,
            SuggestStreamSink sink);

    /** 事件写出端：progress(stage) 可多次；done / error 各为终态（stage 与载荷契约见详设 3.5） */
    interface SuggestStreamSink {

        void progress(String stage);

        void done(BugDraftSuggestRespDTO resp);

        void error(int code, String message);
    }
}
