package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 澄清反问（详设 4.1 ask_user）：仅做入参校验——question 必填，options 至多 5 条且非空。
 * 循环收到本工具调用即落 clarify 事件并终止本轮（选项须来自查询结果或上下文，不得虚构）。
 */
@Component
public class AskUserTool implements AssistantTool {

    private static final int OPTIONS_MAX = 5;
    private static final int OPTION_MAX_LENGTH = 200;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "ask_user",
                "向用户提出澄清反问（question + 2~5 个快捷选项 options）。当信息不足、存在歧义或"
                        + "多个候选需要用户抉择时调用；选项必须来自查询结果或上下文，无法给出选项时省略。"
                        + "调用后本轮立即结束，等待用户回答。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("question", "反问文案，必填"),
                        ToolSchemas.stringArray("options", "快捷选项（2~5 条，无法给出时省略）",
                                OPTIONS_MAX)),
                        List.of("question")),
                ToolCategory.CLARIFY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String question = str(args.get("question")).trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("question is required");
        }
        List<String> options = new ArrayList<>();
        if (args.get("options") instanceof List<?> raw) {
            for (Object element : raw) {
                String option = str(element).trim();
                if (option.isEmpty()) {
                    continue;
                }
                if (options.size() >= OPTIONS_MAX) {
                    throw new IllegalArgumentException("options must be at most " + OPTIONS_MAX);
                }
                options.add(option.length() > OPTION_MAX_LENGTH
                        ? option.substring(0, OPTION_MAX_LENGTH) : option);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("options", options);
        return JsonUtils.toJsonString(result);
    }

    private static String str(Object value) {
        return value instanceof String text ? text : "";
    }
}
