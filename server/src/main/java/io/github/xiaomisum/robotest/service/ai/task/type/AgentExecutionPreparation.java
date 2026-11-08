package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Agent 调用前的可信快照。适配器在准备阶段完成限域读取、媒体装配与输出解析器绑定，
 * 该阶段不得调用模型；执行核只负责渲染同场景提示词和驱动工具循环。
 */
public record AgentExecutionPreparation(Map<String, String> promptVariables, String inputSnapshot,
        List<AiChatMedia> media, Function<String, List<ProposalDraft>> proposalParser,
        Function<String, Map<String, Object>> resultMetaParser) {

    public AgentExecutionPreparation {
        promptVariables = promptVariables == null ? Map.of() : Map.copyOf(promptVariables);
        inputSnapshot = inputSnapshot == null ? "" : inputSnapshot;
        media = media == null ? List.of() : List.copyOf(media);
        proposalParser = proposalParser == null ? output -> List.of() : proposalParser;
        resultMetaParser = resultMetaParser == null ? output -> null : resultMetaParser;
    }
}
