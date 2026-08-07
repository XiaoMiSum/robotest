package io.github.xiaomisum.robotest.service.ai.casegen;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;

import java.util.UUID;

/**
 * AI 遗漏测试点分析（详细设计 3.3 / 4.3，关键词版）：
 * 需求输入归一 → 关键词检索候选用例 → LLM 比对输出遗漏点 → 幻觉过滤。
 */
public interface AiMissingPointService {

    /**
     * 同步分析遗漏测试点：分析范围限当前项目，不自动创建任何用例；
     * saveAsRequirement 失败不阻断分析（3.3）。
     */
    AiMissingPointRespDTO analyze(UUID userId, UUID workspaceId, UUID projectId, AiMissingPointReqDTO reqDTO);
}
