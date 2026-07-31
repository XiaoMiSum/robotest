package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiStepCompleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTextImportReqDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 脑图 AI 生成类功能（US-AI-001 用例子树生成 / US-AI-002 步骤补全 / US-AI-016 外部文本导入）：
 * 归属校验 + 上下文组装 + 网关流式调用
 */
public interface AiCaseGenerationService {

    /**
     * 生成用例子树（SSE：delta 透传 + done 帧携带结构化用例树与 warnings）
     */
    SseEmitter generateCaseTree(UUID userId, UUID workspaceId, UUID projectId, AiCaseGenerateReqDTO reqDTO);

    /**
     * 补全用例步骤（SSE：done 帧为 step/expected 扁平数组，空数组表示无需补全）
     */
    SseEmitter completeSteps(UUID userId, UUID workspaceId, UUID projectId, AiStepCompleteReqDTO reqDTO);

    /**
     * 外部文本解析导入（SSE：done 帧为完整树结构，无法解析时空 nodes + warning）
     */
    SseEmitter importText(UUID userId, UUID workspaceId, UUID projectId, AiTextImportReqDTO reqDTO);
}
