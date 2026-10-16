package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import io.github.xiaomisum.robotest.service.ai.assistant.GuideKnowledgeBase;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识检索（详设 4.1 search_knowledge，二合一）：先静态平台指引片段（角色过滤 + 主题词匹配，
 * 零依赖向量能力、附站内 route），再 RAG 向量命中（限权项目集）；向量不可用时指引仍可应答，
 * 引用（citations）取本工具 hits 由循环聚合落库。
 */
@Component
public class SearchKnowledgeTool implements AssistantTool {

    /** 检索条数与引语长度（与生成链 RAG_TOP_K / RAG_FRAGMENT_CHARS 同口径，详设 4.1） */
    private static final int TOP_K = 8;
    private static final int QUOTE_CHARS = 500;

    @Resource
    private GuideKnowledgeBase guideKnowledgeBase;
    @Resource
    private VectorSearchService vectorSearchService;
    @Resource
    private AssistantRefChecker refChecker;
    @Resource
    private AssistantPromptSupport promptSupport;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "search_knowledge",
                "检索知识：先匹配平台使用指引（如何操作类问题），再对业务知识做向量检索。"
                        + "回答平台怎么用、业务规则说明或依赖既有文档的问题前调用。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("query", "检索文本（基于用户问题提炼）"),
                        ToolSchemas.string("projectId", "限定项目，可空表示活跃空间全部项目")),
                        List.of("query")),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String query = str(args.get("query")).trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query is required");
        }
        UUID projectId = uuid(args.get("projectId"));
        List<UUID> projectIds;
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(context.workspaceId(), projectId);
            projectIds = List.of(projectId);
        } else {
            projectIds = promptSupport.workspaceProjectIds(context.workspaceId());
        }

        List<Map<String, Object>> fragments = new ArrayList<>();
        for (GuideKnowledgeBase.GuideFragment fragment
                : guideKnowledgeBase.search(context.userId(), context.workspaceId(), query)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("topics", fragment.topics());
            item.put("route", fragment.route());
            item.put("content", fragment.content());
            fragments.add(item);
        }

        List<Map<String, Object>> hits = new ArrayList<>();
        String ragError = null;
        try {
            for (AiVectorSearchHitRespDTO hit : vectorSearchService.search(
                    query, projectIds, null, TOP_K, context.userId())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", hit.getEntityType());
                item.put("id", String.valueOf(hit.getEntityId()));
                String title = refChecker.titleOf(hit.getEntityType(), hit.getEntityId());
                item.put("title", title == null ? String.valueOf(hit.getEntityId()) : title);
                item.put("quote", abbreviate(hit.getContent()));
                hits.add(item);
            }
        } catch (ServiceException unavailable) {
            // 向量不可用：静态指引段仍可应答（详设 4.1 降级），错误文本回填模型据实说明
            ragError = unavailable.getCode() + ": " + unavailable.getMessage();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fragments", fragments);
        result.put("hits", hits);
        if (ragError != null) {
            result.put("ragError", ragError);
        }
        return JsonUtils.toJsonString(result);
    }

    private static String abbreviate(String value) {
        String content = value == null ? "" : value;
        return content.length() <= QUOTE_CHARS ? content : content.substring(0, QUOTE_CHARS);
    }

    private static String str(Object value) {
        return value instanceof String text ? text : "";
    }

    private static UUID uuid(Object value) {
        String text = str(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
