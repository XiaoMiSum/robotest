package io.github.xiaomisum.robotest.framework.common;

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
    }

    public interface WebSocket {
        String MSG_UPDATE_LAYOUT = "update_layout";
        String MSG_ADD_NODE = "add_node";
        String MSG_UPDATE_ATTRS = "update_attrs";
        String MSG_DELETE_NODE = "delete_node";
        String MSG_MOVE_NODE = "move_node";
        String MSG_TYPE_ERROR = "error";
        String ERROR_PERSIST_FAILED = "PERSIST_FAILED";
        String USER_ANONYMOUS = "anonymous";
        String PROP_USER_ID = "userId";
        String PROP_LOGIN_USER = "loginUser";
        String PARAM_TOKEN = "token";
    }

    public interface Tree {
        String ROOT_KEY = "root";
    }

    public interface Auth {
        String ROLE_PREFIX = "ROLE_";
        String TOKEN_TYPE_BEARER = "Bearer";
    }
}
