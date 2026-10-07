package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.domain.plan.TestPlanService;
import io.github.xiaomisum.robotest.service.domain.review.TestReviewService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import xyz.migoo.framework.security.core.annotation.AuditLog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 确认执行与取消（详设 3.7 / 3.8、4.2）：校验链 251 → 252 → 255 → 253 → 254 分档 →
 * 256 → 257 → 结构校验，随后条件认领 execution（占位回执）并逐项独立事务落库。
 *
 * <p>回执写 execution 后不可变更：首执由载体条件更新占位（并发下 0 行即 254），
 * 逐项结果以最终写整体覆盖；重试不认领，按既有 results 的 seq 反查回执项后追加，
 * 原条目与 status / executedBy / executedAt 保持不变。</p>
 */
@Slf4j
@Service
public class AssistantExecuteServiceImpl implements AssistantExecuteService {

    /** 与任务中心产物确认同口径的执行权限点（详设 3.7 校验链 257） */
    private static final String PERMISSION_AI_CONFIRM = "ai:confirm";

    private static final String STATUS_EXECUTED = "executed";
    private static final String STATUS_REJECTED = "rejected";

    /** 跳转链接为前端实际路由相对路径（详设 4.2⑦），服务端不拼前缀 */
    private static final String LINK_CASES = "/workspace/projects/functional-testing";
    private static final String LINK_REVIEWS = "/workspace/projects/reviews/";
    private static final String LINK_PLANS = "/workspace/projects/plans/";

    /** 逐项落库异常兜底（500 属框架全局码段豁免，详设 4.2④ 失败项附原因） */
    private static final ErrorCode ITEM_FAILED = ErrorCode.of(500, "执行落库失败");

    /** kind 十种（详设 4.3），与解析侧 AssistantParseHandler.INTENT_KINDS 同口径 */
    private static final Set<String> INTENT_KINDS = Set.of(
            "create_case", "update_case", "complete_case", "batch_tag",
            "create_review", "adjust_review", "view_review_progress",
            "create_plan", "adjust_plan", "view_plan_progress");

    /** 圈选用例是这七种动作的必填输入（详设 4.2⑥），缺失按参数非法拒绝 */
    private static final Set<String> NEED_CASE_IDS = Set.of(
            "update_case", "complete_case", "batch_tag",
            "create_review", "adjust_review", "create_plan", "adjust_plan");

    /** 调整与进度查看类必须指向既有对象，缺失视为意图残缺（1000018255） */
    private static final Set<String> NEED_TARGET_ID = Set.of(
            "adjust_review", "adjust_plan", "view_review_progress", "view_plan_progress");

    @Resource
    private AssistantService assistantService;
    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private TestCaseNodeService testCaseNodeService;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestReviewService testReviewService;
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    @AuditLog(action = "EXECUTE:AiAssistantMessage")
    public AiAssistantExecuteRespDTO execute(UUID conversationId, UUID messageId,
            AiAssistantExecuteReqDTO reqDTO, LoginUser loginUser) {
        assistantService.getOwnedConversation(conversationId, loginUser.getId());
        AiAssistantMessage message = requireMessage(conversationId, messageId);
        Map<String, Object> intent = message.getIntent();
        if (intent == null || intent.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
        }
        requireNotExpired(intent);
        List<Integer> retryIndexes = normalizeRetryIndexes(
                reqDTO == null ? null : reqDTO.getRetryIndexes());
        Map<String, Object> current = message.getExecution();
        List<Map<String, Object>> priorResults = requireRetryTargets(current, retryIndexes);
        requireScope(intent, loginUser);
        requireConfirmPermission(loginUser);

        // 结构校验与回执项装配先于认领：结构非法不消耗 execution，可修复后重来
        UUID userId = loginUser.getId();
        ExecPlan plan = buildPlan(intent, ModelOutputSupport.asMap(intent.get("params")),
                loginUser.getActiveProjectId(), userId);

        if (retryIndexes.isEmpty()) {
            return firstExecute(messageId, plan, userId);
        }
        return retryExecute(messageId, plan, retryIndexes, priorResults, current);
    }

    @Override
    public AiAssistantExecuteRespDTO cancel(UUID conversationId, UUID messageId, LoginUser loginUser) {
        assistantService.getOwnedConversation(conversationId, loginUser.getId());
        AiAssistantMessage message = requireMessage(conversationId, messageId);
        Map<String, Object> intent = message.getIntent();
        if (intent == null || intent.isEmpty()) {
            throw parseFailed();
        }
        if (message.getExecution() != null) {
            throw resolved();
        }
        Map<String, Object> rejected = new LinkedHashMap<>();
        rejected.put("status", STATUS_REJECTED);
        rejected.put("rejectedBy", String.valueOf(loginUser.getId()));
        rejected.put("rejectedAt", Instant.now().toString());
        // 取消同样走条件认领：并发下被抢先执行 / 取消的一方按 254 拒绝
        claim(messageId, rejected);
        return resp(rejected);
    }

    // ---------- 首执与重试 ----------

    private AiAssistantExecuteRespDTO firstExecute(UUID messageId, ExecPlan plan, UUID userId) {
        String executedBy = String.valueOf(userId);
        String executedAt = Instant.now().toString();
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("status", STATUS_EXECUTED);
        placeholder.put("executedBy", executedBy);
        placeholder.put("executedAt", executedAt);
        placeholder.put("results", List.of());
        claim(messageId, placeholder);

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("status", STATUS_EXECUTED);
        execution.put("executedBy", executedBy);
        execution.put("executedAt", executedAt);
        if (plan.viewLink() != null) {
            execution.put("link", plan.viewLink());
        }
        execution.put("results", runAll(plan.units()));
        writeExecution(messageId, execution);
        return resp(execution);
    }

    private AiAssistantExecuteRespDTO retryExecute(UUID messageId, ExecPlan plan,
            List<Integer> retryIndexes, List<Map<String, Object>> priorResults,
            Map<String, Object> current) {
        // 重试无认领环节：按上次回执条目的 seq 反查本轮回执项，单项执行后整体追加
        List<ExecUnit> runUnits = new ArrayList<>();
        for (Integer index : retryIndexes) {
            runUnits.add(unitBySeq(plan.units(), priorResults.get(index)));
        }
        List<Map<String, Object>> merged = new ArrayList<>(priorResults);
        merged.addAll(runAll(runUnits));
        Map<String, Object> execution = new LinkedHashMap<>(current);
        execution.put("results", merged);
        writeExecution(messageId, execution);
        return resp(execution);
    }

    /**
     * 首执占位认领：载体条件写 execution（仅 execution 为空时生效），0 行即已被
     * 并发执行或取消（1000018254）；占位崩溃时保守卡死于已执行态，不产生半份回执。
     */
    private void claim(UUID messageId, Map<String, Object> execution) {
        AiAssistantMessage carrier = new AiAssistantMessage();
        carrier.setId(messageId);
        carrier.setExecution(execution);
        // update(entity, wrapper) 的 SET 取自载体（Jackson3TypeHandler 序列化 jsonb）、
        // WHERE 只取 wrapper，故必须同时限定 id 与 execution IS NULL 才构成原子认领
        int rows = messageMapper.update(carrier, new LambdaUpdateWrapperX<AiAssistantMessage>()
                .eq(AiAssistantMessage::getId, messageId)
                .isNull(AiAssistantMessage::getExecution));
        if (rows == 0) {
            throw resolved();
        }
    }

    /** 终态回执写：载体仅 id + execution（C11 部分更新），整体覆盖占位 */
    private void writeExecution(UUID messageId, Map<String, Object> execution) {
        AiAssistantMessage carrier = new AiAssistantMessage();
        carrier.setId(messageId);
        carrier.setExecution(execution);
        messageMapper.updateById(carrier);
    }

    // ---------- 校验链 ----------

    /** 252：消息须存在且属于该路径会话，同码处理以防跨会话枚举消息 ID */
    private AiAssistantMessage requireMessage(UUID conversationId, UUID messageId) {
        AiAssistantMessage message = messageMapper.selectById(messageId);
        if (message == null || !conversationId.equals(message.getConversationId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_MESSAGE_NOT_FOUND);
        }
        return message;
    }

    /** 253：expiresAt 由解析侧以 LocalDateTime.toString() 固化，缺失或解析失败视同过期 */
    private static void requireNotExpired(Map<String, Object> intent) {
        String expiresAt = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(intent.get("expiresAt")));
        LocalDateTime deadline = null;
        if (expiresAt != null) {
            try {
                deadline = LocalDateTime.parse(expiresAt);
            } catch (DateTimeParseException ignored) {
                // 留空走统一过期分支
            }
        }
        if (deadline == null || LocalDateTime.now().isAfter(deadline)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PREVIEW_EXPIRED);
        }
    }

    /** 254 分档：首执要求 execution 为空；重试要求已 executed 且下标指向既有失败项 */
    private static List<Map<String, Object>> requireRetryTargets(Map<String, Object> current,
            List<Integer> retryIndexes) {
        if (retryIndexes.isEmpty()) {
            if (current != null) {
                throw resolved();
            }
            return List.of();
        }
        if (current == null || !STATUS_EXECUTED.equals(current.get("status"))) {
            throw resolved();
        }
        List<Map<String, Object>> results = resultsOf(current);
        for (Integer index : retryIndexes) {
            if (index == null || index < 0 || index >= results.size()) {
                throw invalid();
            }
            Map<String, Object> entry = results.get(index);
            // 只重试显式 success=false 的条目：成功项与畸形条目一律按参数非法拒绝
            if (!Boolean.FALSE.equals(entry.get("success"))) {
                throw invalid();
            }
        }
        return results;
    }

    private static List<Integer> normalizeRetryIndexes(List<Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<Integer> distinct = new LinkedHashSet<>(raw);
        if (distinct.contains(null)) {
            throw invalid();
        }
        return new ArrayList<>(distinct);
    }

    /** 256：执行以请求头为准，scope 任一维缺失或不一致即拒绝（头缺失同样算不一致） */
    private static void requireScope(Map<String, Object> intent, LoginUser loginUser) {
        Map<String, Object> scope = ModelOutputSupport.asMap(intent.get("scope"));
        if (!scopeEquals(scope.get("workspaceId"), loginUser.getActiveWorkspaceId())
                || !scopeEquals(scope.get("projectId"), loginUser.getActiveProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_SCOPE_MISMATCH);
        }
    }

    private static boolean scopeEquals(Object scopeValue, UUID headerValue) {
        return scopeValue != null && headerValue != null
                && headerValue.toString().equals(String.valueOf(scopeValue));
    }

    private static void requireConfirmPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION_AI_CONFIRM)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_NO_PERMISSION);
        }
    }

    // ---------- 结构校验与回执项装配 ----------

    private ExecPlan buildPlan(Map<String, Object> intent, Map<String, Object> params,
            UUID projectId, UUID userId) {
        String kind = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(intent.get("kind")));
        if (kind == null || !INTENT_KINDS.contains(kind)) {
            throw parseFailed();
        }
        UUID targetId = NEED_TARGET_ID.contains(kind) ? requireTargetId(intent) : null;
        String targetTitle = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(intent.get("targetTitle")));
        List<Map<String, Object>> changes = listOf(intent.get("changes"));
        List<UUID> caseIds = NEED_CASE_IDS.contains(kind) ? requireCaseIds(params) : List.of();

        List<ExecUnit> units = List.of();
        String viewLink = null;
        switch (kind) {
            case "create_case" -> {
                if (targetTitle == null) {
                    throw parseFailed();
                }
                units = createCaseUnits(params, changes, targetTitle, projectId, userId);
            }
            case "update_case", "complete_case" ->
                    units = caseUnits(kind, caseIds, changes, false, projectId, userId);
            case "batch_tag" ->
                    units = caseUnits(kind, caseIds, changes, true, projectId, userId);
            case "create_review" ->
                    units = List.of(reviewCreateUnit(targetTitle, caseIds, params, projectId, userId));
            case "adjust_review" ->
                    units = List.of(reviewAdjustUnit(targetId, caseIds, projectId, userId));
            case "create_plan" ->
                    units = List.of(planCreateUnit(targetTitle, caseIds, params, projectId, userId));
            case "adjust_plan" ->
                    units = List.of(planAdjustUnit(targetId, caseIds, projectId, userId));
            // 进度查看类不落库：results 为空数组，回执仅附跳转链接（详设 4.3）
            case "view_review_progress" -> viewLink = LINK_REVIEWS + targetId;
            case "view_plan_progress" -> viewLink = LINK_PLANS + targetId;
            default -> throw parseFailed();
        }
        return new ExecPlan(units, viewLink);
    }

    /** create_case：params.cases 每条一回执项；缺省按 targetTitle + changes 单条创建 */
    private List<ExecUnit> createCaseUnits(Map<String, Object> params, List<Map<String, Object>> changes,
            String targetTitle, UUID projectId, UUID userId) {
        List<ExecUnit> units = new ArrayList<>();
        if (params.get("cases") instanceof List<?> cases && !cases.isEmpty()) {
            for (Object element : cases) {
                addCreateCaseUnit(units, ModelOutputSupport.asMap(element), params, projectId, userId);
            }
            return units;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", targetTitle);
        for (Map<String, Object> change : changes) {
            String field = ModelOutputSupport.trimToNull(
                    ModelOutputSupport.asString(change.get("field")));
            if (field != null) {
                // 同字段后写覆盖先写；白名单外字段由 createCase 忽略
                fields.put(field, change.get("value"));
            }
        }
        addCreateCaseUnit(units, fields, params, projectId, userId);
        return units;
    }

    private void addCreateCaseUnit(List<ExecUnit> units, Map<String, Object> fields,
            Map<String, Object> params, UUID projectId, UUID userId) {
        int seq = units.size();
        units.add(new ExecUnit(seq, "create_case", Map.of(), () -> {
            UUID created = testCaseNodeService.createCase(projectId, userId,
                    uuidParam(params.get("documentId")), uuidParam(params.get("parentId")), fields);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("caseId", created.toString());
            extra.put("link", LINK_CASES);
            return extra;
        }));
    }

    /** update / complete / batch_tag：每个 caseId 一回执项，changes 原样直传既有服务 */
    private List<ExecUnit> caseUnits(String action, List<UUID> caseIds,
            List<Map<String, Object>> changes, boolean tag, UUID projectId, UUID userId) {
        List<ExecUnit> units = new ArrayList<>();
        for (UUID caseId : caseIds) {
            Map<String, Object> extras = Map.of("caseId", caseId.toString());
            units.add(new ExecUnit(units.size(), action, extras, () -> {
                if (tag) {
                    testCaseNodeService.tagCase(projectId, userId, caseId, changes);
                } else {
                    testCaseNodeService.updateCaseFields(projectId, userId, caseId, changes);
                }
                // 目标 caseId 成败均带（详设 3.7 回执结构），便于前端定位与单项重试
                return Map.of("caseId", caseId.toString(), "link", LINK_CASES);
            }));
        }
        return units;
    }

    private ExecUnit reviewCreateUnit(String targetTitle, List<UUID> caseIds,
            Map<String, Object> params, UUID projectId, UUID userId) {
        return new ExecUnit(0, "create_review", Map.of(), () -> {
            if (targetTitle == null) {
                throw invalid();
            }
            TestReviewCreateReqDTO dto = new TestReviewCreateReqDTO();
            dto.setTitle(targetTitle);
            dto.setDescription(stringParam(params.get("description")));
            dto.setParticipantIds(participantIds(params, userId));
            dto.setSelectedNodes(reviewNodes(groupByDocument(caseIds)));
            TestReviewDetailRespDTO review = testReviewService.createReview(projectId, userId, dto);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("createdId", review.getId().toString());
            extra.put("link", LINK_REVIEWS + review.getId());
            return extra;
        });
    }

    private ExecUnit reviewAdjustUnit(UUID targetId, List<UUID> caseIds, UUID projectId, UUID userId) {
        return new ExecUnit(0, "adjust_review", Map.of(), () -> {
            TestReviewCasesUpdateReqDTO dto = new TestReviewCasesUpdateReqDTO();
            dto.setSelectedNodes(reviewNodes(groupByDocument(caseIds)));
            testReviewService.updateReviewCases(projectId, targetId, userId, dto);
            return Map.of("link", LINK_REVIEWS + targetId);
        });
    }

    private ExecUnit planCreateUnit(String targetTitle, List<UUID> caseIds,
            Map<String, Object> params, UUID projectId, UUID userId) {
        return new ExecUnit(0, "create_plan", Map.of(), () -> {
            if (targetTitle == null) {
                throw invalid();
            }
            TestPlanCreateReqDTO dto = new TestPlanCreateReqDTO();
            dto.setName(targetTitle);
            dto.setDescription(stringParam(params.get("description")));
            dto.setEnvironment(stringParam(params.get("environment")));
            dto.setExecutorId(uuidParam(params.get("executorId")));
            // 时间解析失败按参数非法单项失败，不静默丢弃（详设 4.2⑥）
            dto.setStartTime(planTime(params.get("startTime")));
            dto.setEndTime(planTime(params.get("endTime")));
            dto.setSelectedNodes(planNodes(groupByDocument(caseIds)));
            TestPlanDetailRespDTO plan = testPlanService.createPlan(projectId, userId, dto);
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("createdId", plan.getId().toString());
            extra.put("link", LINK_PLANS + plan.getId());
            return extra;
        });
    }

    private ExecUnit planAdjustUnit(UUID targetId, List<UUID> caseIds, UUID projectId, UUID userId) {
        return new ExecUnit(0, "adjust_plan", Map.of(), () -> {
            TestPlanCasesUpdateReqDTO dto = new TestPlanCasesUpdateReqDTO();
            dto.setSelectedNodes(planNodes(groupByDocument(caseIds)));
            testPlanService.updatePlanCases(projectId, targetId, userId, dto);
            return Map.of("link", LINK_PLANS + targetId);
        });
    }

    // ---------- 装配辅助 ----------

    /**
     * caseIds → 按节点自身 documentId 分组（保序），供评审 / 计划 SelectedNode 转换；
     * 任一节点已失效按 1000011022 单项失败（详设 4.2⑥ 圈选用例存在性）。
     */
    private Map<UUID, List<UUID>> groupByDocument(List<UUID> caseIds) {
        Map<UUID, TestCaseNode> nodes = new LinkedHashMap<>();
        for (TestCaseNode node : testCaseNodeMapper.listByIds(caseIds)) {
            nodes.put(node.getId(), node);
        }
        Map<UUID, List<UUID>> grouped = new LinkedHashMap<>();
        for (UUID caseId : caseIds) {
            TestCaseNode node = nodes.get(caseId);
            if (node == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
            }
            grouped.computeIfAbsent(node.getDocumentId(), key -> new ArrayList<>()).add(caseId);
        }
        return grouped;
    }

    /** 评审与计划的 SelectedNode 是各自 DTO 的内部类，先经共享分组再分别转换 */
    private static List<TestReviewCreateReqDTO.SelectedNode> reviewNodes(
            Map<UUID, List<UUID>> grouped) {
        List<TestReviewCreateReqDTO.SelectedNode> nodes = new ArrayList<>();
        grouped.forEach((documentId, caseIds) -> {
            TestReviewCreateReqDTO.SelectedNode node = new TestReviewCreateReqDTO.SelectedNode();
            node.setDocumentId(documentId);
            node.setCaseIds(caseIds);
            nodes.add(node);
        });
        return nodes;
    }

    private static List<TestPlanCreateReqDTO.SelectedNode> planNodes(
            Map<UUID, List<UUID>> grouped) {
        List<TestPlanCreateReqDTO.SelectedNode> nodes = new ArrayList<>();
        grouped.forEach((documentId, caseIds) -> {
            TestPlanCreateReqDTO.SelectedNode node = new TestPlanCreateReqDTO.SelectedNode();
            node.setDocumentId(documentId);
            node.setCaseIds(caseIds);
            nodes.add(node);
        });
        return nodes;
    }

    /** create_review 参与者：未携带取执行人本人（详设 4.2⑥），携带则逐个解析校验 */
    private static List<UUID> participantIds(Map<String, Object> params, UUID userId) {
        if (!(params.get("participantIds") instanceof List<?> list) || list.isEmpty()) {
            return List.of(userId);
        }
        List<UUID> ids = new ArrayList<>();
        for (Object element : list) {
            String value = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(element));
            if (value == null) {
                throw invalid();
            }
            ids.add(parseUuid(value));
        }
        return ids;
    }

    private static List<UUID> requireCaseIds(Map<String, Object> params) {
        List<UUID> ids = new ArrayList<>();
        if (params.get("caseIds") instanceof List<?> list) {
            for (Object element : list) {
                String value = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(element));
                if (value == null) {
                    throw invalid();
                }
                ids.add(parseUuid(value));
            }
        }
        if (ids.isEmpty()) {
            throw invalid();
        }
        return ids;
    }

    /** 255：调整与进度查看类必须指向既有对象，取值非法同按意图残缺处理 */
    private static UUID requireTargetId(Map<String, Object> intent) {
        String value = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(intent.get("targetId")));
        if (value == null) {
            throw parseFailed();
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw parseFailed();
        }
    }

    private static UUID uuidParam(Object raw) {
        String value = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw));
        return value == null ? null : parseUuid(value);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    /** 计划时间三段解析：ISO 本地时间 → 偏移时间取墙钟 → 纯日期零点，皆失按参数非法 */
    private static LocalDateTime planTime(Object raw) {
        String text = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw));
        if (text == null) {
            return null;
        }
        String iso = text.replace(' ', 'T');
        try {
            return LocalDateTime.parse(iso);
        } catch (DateTimeParseException ignored) {
            // 继续按带时区格式尝试
        }
        try {
            return OffsetDateTime.parse(iso).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 继续按纯日期尝试
        }
        try {
            return LocalDate.parse(iso).atStartOfDay();
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    private static String stringParam(Object raw) {
        return raw instanceof String text ? text : null;
    }

    // ---------- 回执执行 ----------

    private List<Map<String, Object>> runAll(List<ExecUnit> units) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (ExecUnit unit : units) {
            runUnit(unit, results);
        }
        return results;
    }

    /** 逐项独立事务（详设 4.2④）：成功项落库保留，失败项整体回滚并附错误码可单项重试 */
    private void runUnit(ExecUnit unit, List<Map<String, Object>> results) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        try {
            template.execute(status -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("seq", unit.seq());
                entry.put("action", unit.action());
                entry.put("success", true);
                entry.putAll(unit.body().get());
                results.add(entry);
                return null;
            });
        } catch (ServiceException e) {
            results.add(failure(unit, e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.warn("[AI] 助手执行逐项失败 seq={} action={}", unit.seq(), unit.action(), e);
            results.add(failure(unit, ITEM_FAILED.code(), ITEM_FAILED.msg()));
        }
    }

    private static ExecUnit unitBySeq(List<ExecUnit> units, Map<String, Object> prior) {
        Object seq = prior.get("seq");
        if (!(seq instanceof Number number) || number.intValue() < 0
                || number.intValue() >= units.size()) {
            throw invalid();
        }
        return units.get(number.intValue());
    }

    private static Map<String, Object> failure(ExecUnit unit, int code, String message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("seq", unit.seq());
        entry.put("action", unit.action());
        entry.put("success", false);
        entry.putAll(unit.failureExtras());
        entry.put("errorCode", code);
        entry.put("errorMsg", message);
        return entry;
    }

    // ---------- 通用小工具 ----------

    private static List<Map<String, Object>> listOf(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object element : list) {
            items.add(ModelOutputSupport.asMap(element));
        }
        return items;
    }

    private static List<Map<String, Object>> resultsOf(Map<String, Object> execution) {
        return listOf(execution.get("results"));
    }

    private static AiAssistantExecuteRespDTO resp(Map<String, Object> execution) {
        AiAssistantExecuteRespDTO resp = new AiAssistantExecuteRespDTO();
        resp.setExecution(execution);
        return resp;
    }

    private static ServiceException parseFailed() {
        return ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
    }

    private static ServiceException resolved() {
        return ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PREVIEW_RESOLVED);
    }

    private static ServiceException invalid() {
        return ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
    }

    private record ExecPlan(List<ExecUnit> units, String viewLink) {
    }

    /** 单个回执项：seq 为 units 数组下标，重试按此反查；failureExtras 为失败时仍要附带的上下文 */
    private record ExecUnit(int seq, String action, Map<String, Object> failureExtras,
                            Supplier<Map<String, Object>> body) {
    }
}
