package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseGenerationSupport;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.modelFailed;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parsedArtifacts;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 脑图生成用例处理器（辅助功能详设 3.4 / 4.4）：单条已确认需求 + 当前文档发起，
 * 提交期冻结需求快照并校验归属 / 状态 / 同输入去重；执行期以快照与可选 RAG 装配上下文，
 * 逐用例产出一条 mindmap_case_suggestion 提案待人工在脑图面板勾选插入。
 */
@Slf4j
@Component
public class MindmapCaseGenerationTypeAdapter extends AbstractOrdinaryTaskTypeAdapter {

    public static final String TYPE = Constants.AiTaskType.MINDMAP_CASE_GENERATION;

    /** 单次生成的用例数上限，防止模型输出撑爆脑图树 */
    private static final int MAX_CASES = 30;
    private static final int RAG_TOP_K = 8;
    private static final int RAG_FRAGMENT_CHARS = 800;

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiTaskMapper aiTaskMapper;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试用例设计专家。围绕下方单条已确认需求生成可直接执行的测试用例，只输出 JSON，
                不输出解释或代码块标记以外的任何文字。
                1. artifacts 数组逐用例一条，每条给出 title（用例标题）与 content；
                2. content 必含 priority（P0 / P1 / P2 / P3）、precondition / steps / expected / tags
                   （均为字符串数组），steps 与 expected 按序一一对应；
                3. content.sourceRefs 必填，只引用下方需求 ID，quote 为需求原文摘录（至多 300 字）；
                   无有效来源的用例不要输出；
                4. 参考「已存在用例标题」避免生成标题完全相同的重复用例；只生成新用例，不修改既有节点；
                5. 用例数不超过 30 条，覆盖主流程、关键分支与异常场景。
                输出结构（JSON 对象）：
                {"artifacts":[{"title":"获取验证码","content":{"priority":"P0",
                "precondition":["用户处于登录页"],"steps":["输入手机号","点击获取验证码"],
                "expected":["提示验证码已发送"],"tags":["登录"],
                "sourceRefs":[{"requirementId":"…","quote":"…"}]}}]}

                {{requirementContext}}

                {{documentContext}}

                {{ragContext}}
                """;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        CaseAssistSupport.requireDocumentId(input);
        requireRequirementId(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        validateInput(input);
        if (context == null || context.projectId() == null) {
            // 生成发起必经 X-Active-Project（C4）：头缺失属上下文问题，不冒充输入非法
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        UUID projectId = context.projectId();
        UUID documentId = CaseAssistSupport.requireDocumentId(input);
        UUID requirementId = requireRequirementId(input);

        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null || !projectId.equals(requirement.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.MINDMAP_GENERATION_REQUIREMENT_NOT_FOUND);
        }
        if (!Constants.RequirementStatus.CONFIRMED.equals(requirement.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.MINDMAP_GENERATION_REQUIREMENT_NOT_CONFIRMED);
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null || !projectId.equals(document.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.MINDMAP_GENERATION_DOCUMENT_NOT_FOUND);
        }
        requireNoInProgressTwin(projectId, documentId, requirementId);
        // 冻结需求快照：执行期按快照继续，需求后续变更只作面板提示，不改变本次生成
        input.put("requirementSnapshot", CaseGenerationSupport.snapshotOf(requirement));
    }

    @Override
    public String sourceTitle(Map<String, Object> input, TaskSubmitContext context) {
        Requirement requirement = requirementMapper.selectById(requireRequirementId(input));
        return requirement == null ? null : requirement.getTitle();
    }

    @Override
    public AgentExecutionPreparation prepare(TaskTypeAdapter.PreparationContext context) {
        Map<String, Object> input = context.input() == null ? Map.of() : context.input();
        UUID documentId = CaseAssistSupport.requireDocumentId(input);
        UUID requirementId = requireRequirementId(input);

        Map<String, Object> snapshot = asMap(input.get("requirementSnapshot"));
        if (trimToNull(asString(snapshot.get("id"))) == null) {
            Requirement requirement = requirementMapper.selectById(requirementId);
            if (requirement != null) {
                snapshot = CaseGenerationSupport.snapshotOf(requirement);
            }
        }
        Requirement requirement = requirementMapper.selectById(requirementId);
        Map<UUID, Requirement> requirementById = new HashMap<>();
        if (requirement != null && requirementId.equals(requirement.getId())) {
            requirementById.put(requirementId, requirement);
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        List<String> existingTitles = testCaseNodeMapper.listCasesByDocumentIds(List.of(documentId)).stream()
                .map(TestCaseNode::getTitle)
                .filter(Objects::nonNull)
                .filter(title -> !title.isBlank())
                .distinct()
                .toList();

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("requirementContext", CaseGenerationSupport.requirementContext(snapshot));
        variables.put("documentContext", CaseGenerationSupport.documentContext(
                document == null ? null : document.getName(), existingTitles));
        variables.put("ragContext", ragContext(context.projectId(), context.userId(), requirement));

        return new AgentExecutionPreparation(variables, String.join("\n", variables.values()), List.of(),
                output -> parseCases(output, requirementById), output -> null);
    }

    /** 无工具：需求快照与 RAG 已在 prepare 装配，执行核退化为一次性决策（工具白名单为空） */
    @Override
    public List<String> toolCodes() {
        return List.of();
    }

    @Override
    protected String defaultProposalKind() {
        return Constants.AiProposalKind.MINDMAP_CASE_SUGGESTION;
    }

    private static UUID requireRequirementId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("requirementId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 同项目同类型存在同 documentId + requirementId 的进行中任务时拒绝（1000018312） */
    private void requireNoInProgressTwin(UUID projectId, UUID documentId, UUID requirementId) {
        Map<String, Object> needle = submission(documentId, requirementId);
        boolean exists = aiTaskMapper.listInProgressByType(projectId, TYPE).stream()
                .anyMatch(task -> needle.equals(submission(
                        parseUuidOrNull(task.getInput() == null ? null : task.getInput().get("documentId")),
                        parseUuidOrNull(task.getInput() == null ? null : task.getInput().get("requirementId")))));
        if (exists) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.MINDMAP_GENERATION_DUPLICATE_TASK);
        }
    }

    private static Map<String, Object> submission(UUID documentId, UUID requirementId) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("documentId", documentId == null ? null : documentId.toString());
        normalized.put("requirementId", requirementId == null ? null : requirementId.toString());
        return normalized;
    }

    private static UUID parseUuidOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** RAG 增强：向量未配置 / 重建中 / 检索失败时降级为无检索，不阻断本次生成 */
    private String ragContext(UUID projectId, UUID userId, Requirement requirement) {
        if (requirement == null || projectId == null) {
            return "";
        }
        try {
            String query = trimToNull(requirement.getTitle());
            if (query == null) {
                return "";
            }
            List<AiVectorSearchHitRespDTO> hits = vectorSearchService.search(query,
                    List.of(projectId), Constants.EntityType.REQUIREMENT, RAG_TOP_K, userId);
            List<String> lines = hits.stream()
                    .filter(hit -> requirement.getId().equals(hit.getEntityId()))
                    .map(hit -> "- " + abbreviate(String.valueOf(hit.getContent()).replace('\n', ' '),
                            RAG_FRAGMENT_CHARS))
                    .distinct()
                    .toList();
            if (lines.isEmpty()) {
                return "";
            }
            return "\n相关需求片段（向量检索命中）：\n" + String.join("\n", lines);
        } catch (ServiceException e) {
            // 向量底座未就绪属可降级场景，生成链仍按需求快照继续
            log.debug("[AI] 脑图生成用例 RAG 不可用，降级为无检索：{}", e.getMessage());
            return "";
        }
    }

    private List<ProposalDraft> parseCases(String output, Map<UUID, Requirement> requirementById) {
        List<Map<String, Object>> artifacts = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> item : parsedArtifacts(new AiChatReply(output, 0, 0))) {
            if (artifacts.size() >= MAX_CASES) {
                break;
            }
            Map<String, Object> raw = asMap(item.get("content"));
            Map<String, Object> content = CaseGenerationSupport.sanitizeContent(raw, requirementById);
            if (content == null) {
                log.debug("丢弃缺少有效来源的脑图生成用例提案 title={}", item.get("title"));
                continue;
            }
            index++;
            String title = trimToNull(asString(item.get("title")));
            if (title == null) {
                title = trimToNull(asString(raw.get("title")));
            }
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("key", "case-" + index);
            artifact.put("kind", Constants.AiProposalKind.MINDMAP_CASE_SUGGESTION);
            artifact.put("parentKey", null);
            artifact.put("title", title == null ? "AI 生成用例 " + index : title);
            artifact.put("content", content);
            artifact.put("confirmStatus", Constants.AiProposalConfirmStatus.PENDING);
            artifacts.add(artifact);
        }
        if (artifacts.isEmpty()) {
            throw modelFailed("模型未产出有效用例");
        }
        return ProposalDrafts.fromArtifacts(artifacts);
    }
}
