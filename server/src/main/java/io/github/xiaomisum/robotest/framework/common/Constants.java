package io.github.xiaomisum.robotest.framework.common;

import java.util.Set;
import java.util.UUID;

public final class Constants {

    private Constants() {
    }

    public interface Status {
        String ACTIVE = "active";
        String ARCHIVED = "archived";
        String DISSOLVED = "dissolved";
        String REVOKED = "revoked";
        String DISABLED = "disabled";
        /** 用户锁定（V1.2）：与 disabled 同样阻止登录，仅提示文案不同 */
        String LOCKED = "locked";
        String NEW = "new";
        String IN_PROGRESS = "in_progress";
        String CLOSED = "closed";
        String COMPLETED = "completed";
        /** 计划阻塞态（V1.2）：暂停执行，仅可由负责人恢复为 in_progress */
        String BLOCKED = "blocked";
        String REJECTED = "rejected";
        String UNTESTED = "untested";
        String ASSIGNED = "assigned";
        String FIXING = "fixing";
    }

    /**
     * 缺陷状态常量（禅道式三态模型）
     * <p>
     * 状态机：ACTIVE → RESOLVED → CLOSED，重开：RESOLVED/CLOSED → ACTIVE（需填写说明）
     */
    public interface BugStatus {
        String ACTIVE = "active";
        String RESOLVED = "resolved";
        String REJECTED = "rejected";
        String CLOSED = "closed";
    }

    /**
     * 需求状态（详设 4.2 状态机：draft→confirm→confirmed→改题/描述/模块→changed；archived 只读，取消归档回 draft）
     */
    public interface RequirementStatus {
        String DRAFT = "draft";
        String CONFIRMED = "confirmed";
        String CHANGED = "changed";
        String ARCHIVED = "archived";
    }

    /**
     * AI 任务状态（详设 4.2 生命周期：pending → running → succeeded / failed；pending / running → cancelled）
     */
    public interface AiTaskStatus {
        String PENDING = "pending";
        String RUNNING = "running";
        String SUCCEEDED = "succeeded";
        String FAILED = "failed";
        String CANCELLED = "cancelled";
    }

    /**
     * 产物确认动作（详设 3.6.5，ai_artifact_confirm.action 取值）
     */
    public interface AiArtifactAction {
        String ADOPTED = "adopted";
        String ADOPTED_EDITED = "adopted_edited";
        String REJECTED = "rejected";
    }

    /**
     * AI 产物种类（生成链详设 3.4）：与追溯目标节点类型对齐，圈选建议以任务 type 区分
     */
    public interface AiArtifactKind {
        String MODULE = "module_suggestion";
        String MINDMAP_DOCUMENT = "mindmap_document_suggestion";
        String TEST_CASE = "test_case_suggestion";
        String REVIEW_SELECTION = "review_selection";
        String PLAN_SELECTION = "plan_selection";
        String BUG_CLASSIFY = "classify_suggestion";
        String BUG_TRIAGE = "triage_order";
        String BUG_SUMMARY = "summary";
        String BUG_DUPLICATE_GROUP = "duplicate_group";
    }

    /**
     * 助手消息状态（总册 2.9）：流式中 → 完成；断连中止与解析失败分别置 interrupted / error
     */
    public interface AiAssistantMessageStatus {
        String STREAMING = "streaming";
        String DONE = "done";
        String INTERRUPTED = "interrupted";
        String ERROR = "error";
    }

    /**
     * 助手消息角色（总册 2.9）
     */
    public interface AiAssistantMessageRole {
        String USER = "user";
        String ASSISTANT = "assistant";
    }

    /**
     * 追溯边类型（追溯矩阵详设 2.4）：derivation 需求侧派生，case_snapshot 用例 ⇢ 评审 / 计划圈选
     */
    public interface TraceEdgeType {
        String DERIVATION = "derivation";
        String CASE_SNAPSHOT = "case_snapshot";
    }

    /**
     * 追溯边状态（追溯矩阵详设 2.4 状态机）；detached 不参与任何遍历与统计且 AI 不得重建
     */
    public interface TraceEdgeStatus {
        String AI_CREATED = "ai_created";
        String CONFIRMED = "confirmed";
        String CONFLICT = "conflict";
        String STALE = "stale";
        String DETACHED = "detached";
    }

    /**
     * 追溯边建立方式（追溯矩阵详设 2.2）
     */
    public interface TraceEstablishedBy {
        String AI = "ai";
        String MANUAL = "manual";
    }

    /**
     * 追溯节点类型（追溯矩阵详设 2.2 多态关联 source_type / target_type）
     */
    public interface TraceNodeType {
        String REQUIREMENT = "requirement";
        String MODULE = "module";
        String MINDMAP_DOCUMENT = "mindmap_document";
        String TEST_CASE = "test_case";
        String TEST_REVIEW = "test_review";
        String TEST_PLAN = "test_plan";
    }

    /**
     * 覆盖状态（追溯矩阵详设 2.4）：PENDING 为「待分析」，由查询侧按无记录推导，不落库
     */
    public interface TraceCoverageStatus {
        String COVERED = "covered";
        String PARTIAL = "partial";
        String UNCOVERED = "uncovered";
        String PENDING = "pending";
    }

    /**
     * 影响处置标记（追溯矩阵详设 3.10）：null 表示未纳入影响分析，不出现在受影响项列表
     */
    public interface TraceDisposition {
        String PENDING = "pending";
        String REGENERATE = "regenerate";
        String RE_REVIEW = "re_review";
        String NO_IMPACT = "no_impact";
    }

    /**
     * 链路视图遍历方向（追溯矩阵详设 3.3）
     */
    public interface TraceDirection {
        String DOWN = "down";
        String UP = "up";
        String BOTH = "both";
    }

    /**
     * 追溯边修正动作（追溯矩阵详设 3.6）
     */
    public interface TraceEdgeAction {
        String CONFIRM = "confirm";
        String REATTACH = "reattach";
        String DETACH = "detach";
        String RESTORE = "restore";
    }

    /**
     * 缺口类型与引导（追溯矩阵详设 3.9）：suggestedAction 三取值按缺口类型映射
     */
    public interface TraceGapType {
        String UNCOVERED_REQUIREMENT = "uncovered_requirement";
        String ORPHAN_CASE = "orphan_case";
        String UNREVIEWED_CASE = "unreviewed_case";
        String UNSCHEDULED_CASE = "unscheduled_case";
    }

    /**
     * 缺陷类型（对齐禅道分类，枚举值采用语义化 snake_case）
     */
    public interface BugType {
        String CODE_ERROR = "code_error";
        String UI_IMPROVEMENT = "ui_improvement";
        String DESIGN_DEFECT = "design_defect";
        String CONFIGURATION = "configuration";
        String INSTALLATION = "installation";
        String SECURITY = "security";
        String PERFORMANCE = "performance";
        String STANDARD_SPEC = "standard_spec";
        String OTHER = "other";
    }

    /**
     * 缺陷严重等级
     */
    public interface BugSeverity {
        String FATAL = "fatal";
        String SERIOUS = "serious";
        String GENERAL = "general";
        String MINOR = "minor";
    }

    /**
     * 缺陷优先级
     */
    public interface BugPriority {
        String HIGH = "high";
        String MEDIUM = "medium";
        String LOW = "low";
    }

    /**
     * 缺陷解决方案（解决时必填，duplicate 需指定原始 Bug）
     */
    public interface BugResolution {
        String FIXED = "fixed";
        String BY_DESIGN = "by_design";
        String DUPLICATE = "duplicate";
        String EXTERNAL = "external";
        String CANNOT_REPRODUCE = "cannot_reproduce";
        String DEFERRED = "deferred";
        String WONT_FIX = "wont_fix";
    }

    public interface RoleType {
        String SYSTEM = "system";
        String WORKSPACE = "workspace";
    }

    public interface ModuleType {
        String DIRECTORY = "directory";
        String DOCUMENT = "document";
    }

    public interface NodeType {
        String NORMAL = "normal";
        String CASE = "case";
        String PRECONDITION = "precondition";
        String STEP = "step";
        String EXPECTED = "expected";
    }

    public interface ReviewMark {
        String PASS = "pass";
        String FAIL = "fail";
        // 待评审：仅作为 API 交互值，落库时 last_mark 置 NULL 回到初始态
        String PENDING = "pending";
    }

    public interface ReviewOperation {
        String MARK = "mark";
        String COMMENT = "comment";
    }

    // 计划执行结果：block 与前端 ExecutionResult 联合类型保持一致（非 blocked）
    public interface ExecutionResult {
        String PASS = "pass";
        String FAIL = "fail";
        String BLOCK = "block";
        String UNTESTED = "untested";
    }

    public interface BugOperation {
        String CREATE = "create";
        String UPDATE = "update";
        String ASSIGN = "assign";
        String CONFIRM = "confirm";
        String RESOLVE = "resolve";
        String REJECT = "reject";
        String CLOSE = "close";
        String REOPEN = "reopen";
        // 旧六态模型日志类型，保留以兼容历史日志展示
        String STATUS_CHANGE = "status_change";
        String ATTACHMENT_UPLOAD = "attachment_upload";
        String ATTACHMENT_DELETE = "attachment_delete";
    }

    public interface WebSocket {
        String MSG_UPDATE_LAYOUT = "update_layout";
        String MSG_ADD_NODE = "add_node";
        String MSG_UPDATE_ATTRS = "update_attrs";
        String MSG_DELETE_NODE = "delete_node";
        String MSG_MOVE_NODE = "move_node";
        String MSG_TYPE_ERROR = "error";

        /**
         * 可写文本帧类型（安全规范 §4 / 实时协议 78 号 4.3）：转发与持久化前必须校验编辑权限。
         * 与 DocumentPersistenceHandler.persist 的写分支一一对应，新增持久化类型时须同步登记。
         */
        Set<String> WRITE_MSG_TYPES = Set.of(MSG_UPDATE_LAYOUT, MSG_ADD_NODE, MSG_UPDATE_ATTRS,
                MSG_DELETE_NODE, MSG_MOVE_NODE);
    }

    public interface Tree {
        String ROOT_KEY = "root";
    }

    public interface Auth {
        String ROLE_PREFIX = "ROLE_";
        String TOKEN_TYPE_BEARER = "Bearer";
    }

    /**
     * 预置工作空间角色 ID（与 V5 迁移脚本一致）
     */
    public interface WorkspaceRole {
        UUID ADMIN_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
        UUID MEMBER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    }
}
