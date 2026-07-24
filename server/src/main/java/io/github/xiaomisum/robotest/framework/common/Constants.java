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
        String NEW = "new";
        String IN_PROGRESS = "in_progress";
        String CLOSED = "closed";
        String COMPLETED = "completed";
        String UNTESTED = "untested";
        String ASSIGNED = "assigned";
        String FIXING = "fixing";
    }

    /**
     * 缺陷状态流转常量
     * <p>
     * 状态机：NEW → ASSIGNED → FIXING → FIXED → VERIFIED → CLOSED
     * 重开：CLOSED/VERIFIED → FIXING（需填写说明）
     */
    public interface BugStatus {
        String NEW = "new";
        String ASSIGNED = "assigned";
        String FIXING = "fixing";
        String FIXED = "fixed";
        String VERIFIED = "verified";
        String CLOSED = "closed";
    }

    /**
     * 缺陷状态流转合法路径
     */
    public interface BugTransition {
        String NEW_TO_ASSIGNED = "new→assigned";
        String ASSIGNED_TO_FIXING = "assigned→fixing";
        String FIXING_TO_FIXED = "fixing→fixed";
        String FIXED_TO_VERIFIED = "fixed→verified";
        String VERIFIED_TO_CLOSED = "verified→closed";
        String VERIFIED_TO_FIXING = "verified→fixing";
        String CLOSED_TO_FIXING = "closed→fixing";
    }

    public interface RoleType {
        String SYSTEM = "system";
    }

    public interface ModuleType {
        String DIRECTORY = "directory";
        String DOCUMENT = "document";
    }

    public interface NodeType {
        String NORMAL = "normal";
        String CASE = "case";
    }

    public interface ReviewMark {
        String PASS = "pass";
        String FAIL = "fail";
    }

    public interface ReviewOperation {
        String MARK = "mark";
    }

    public interface BugOperation {
        String CREATE = "create";
        String UPDATE = "update";
        String ASSIGN = "assign";
        String STATUS_CHANGE = "status_change";
        String REOPEN = "reopen";
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
}
