package io.github.xiaomisum.robotest.framework.common;

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
        String NEW = "new";
        String IN_PROGRESS = "in_progress";
        String CLOSED = "closed";
        String COMPLETED = "completed";
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

    /**
     * AI 异步任务类型（ai_analysis_task.type）
     */
    public interface AiTaskType {
        String REVIEW_CHECK = "review_check";
        String REVIEW_SUMMARY = "review_summary";
        String BUG_CLUSTERING = "bug_clustering";
        String EMBEDDING_REBUILD = "embedding_rebuild";
        String PLAN_ORDER_RECOMMEND = "plan_order_recommend";
    }

    /**
     * AI 异步任务状态（ai_analysis_task.status）
     */
    public interface AiTaskStatus {
        String PENDING = "pending";
        String RUNNING = "running";
        String SUCCESS = "success";
        String FAILED = "failed";
        String CANCELLED = "cancelled";
    }

    /**
     * AI 调用审计状态（ai_invocation_log.status）
     */
    public interface AiInvocationStatus {
        String SUCCESS = "success";
        String FAILED = "failed";
        String CANCELLED = "cancelled";
        String RATE_LIMITED = "rate_limited";
        String SCHEMA_INVALID = "schema_invalid";
    }

    /**
     * 语义检索能力状态（GET /api/workspace/ai/status 的 semanticSearch 取值）
     */
    public interface AiSemanticSearch {
        String AVAILABLE = "available";
        String DEGRADED = "degraded";
        String UNAVAILABLE = "unavailable";
    }
}
