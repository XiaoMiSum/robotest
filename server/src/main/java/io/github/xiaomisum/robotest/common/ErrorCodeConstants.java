package io.github.xiaomisum.robotest.common;

import xyz.migoo.framework.common.exception.ErrorCode;

public class ErrorCodeConstants {

    // ========== 参数校验 1001-1009 ==========
    public static final ErrorCode VALIDATION_FAILED = ErrorCode.of(1001, "参数校验失败");
    public static final ErrorCode USERNAME_EXISTS = ErrorCode.of(1002, "用户名已存在");
    public static final ErrorCode EMAIL_EXISTS = ErrorCode.of(1003, "邮箱已存在");
    public static final ErrorCode WORKSPACE_NAME_EXISTS = ErrorCode.of(1004, "工作空间名称已存在");
    public static final ErrorCode ROLE_NAME_EXISTS = ErrorCode.of(1005, "角色名称已存在");
    public static final ErrorCode PASSWORD_TOO_WEAK = ErrorCode.of(1006, "密码强度不符合要求");
    public static final ErrorCode ROLE_TYPE_ERROR = ErrorCode.of(1008, "角色类型错误（只能选择系统角色）");
    public static final ErrorCode MUST_KEEP_ONE_WORKSPACE_ADMIN = ErrorCode.of(1009, "必须保留至少一个空间管理员");

    // ========== 权限校验 2001-2006 ==========
    public static final ErrorCode NO_PERMISSION = ErrorCode.of(2001, "无权限执行此操作");
    public static final ErrorCode CANNOT_OPERATE_SELF = ErrorCode.of(2002, "不可操作自身账户");
    public static final ErrorCode SYSTEM_ROLE_NOT_DELETABLE = ErrorCode.of(2003, "系统预置角色不可删除");
    public static final ErrorCode SYSTEM_ROLE_PERMISSION_NOT_MODIFIABLE = ErrorCode.of(2004, "系统预置角色权限不可修改");
    public static final ErrorCode ACCOUNT_DISABLED = ErrorCode.of(2005, "账户已被禁用或登录凭证失效");
    public static final ErrorCode CANNOT_REMOVE_LAST_SYSTEM_ROLE = ErrorCode.of(2006, "不能移除自己的最后一个系统角色");

    // ========== 数据不存在 3001-3003 ==========
    public static final ErrorCode USER_NOT_FOUND = ErrorCode.of(3001, "用户不存在");
    public static final ErrorCode WORKSPACE_NOT_FOUND = ErrorCode.of(3002, "工作空间不存在");
    public static final ErrorCode ROLE_NOT_FOUND = ErrorCode.of(3003, "角色不存在");

    // ========== 冲突 4001-4002 ==========
    public static final ErrorCode WORKSPACE_HAS_PROJECTS = ErrorCode.of(4001, "工作空间下存在项目，无法解散");
    public static final ErrorCode ROLE_IN_USE = ErrorCode.of(4002, "角色被用户引用，无法删除");

    // ========== 服务器错误 ==========
    public static final ErrorCode INTERNAL_SERVER_ERROR = ErrorCode.of(5000, "服务器内部错误");

    // ========== 空间管理模块 1020-1032 ==========
    public static final ErrorCode PROJECT_NAME_EXISTS = ErrorCode.of(1020, "项目名称在当前工作空间已存在");
    public static final ErrorCode PROJECT_NOT_FOUND = ErrorCode.of(1021, "项目不存在或不属于当前工作空间");
    public static final ErrorCode PROJECT_HAS_ACTIVE_PLANS = ErrorCode.of(1022, "项目下存在进行中的测试计划，无法归档");
    public static final ErrorCode PROJECT_HAS_DATA = ErrorCode.of(1023, "项目下存在数据，无法删除");
    public static final ErrorCode USER_ALREADY_IN_WORKSPACE = ErrorCode.of(1024, "用户已在工作空间中");
    public static final ErrorCode USER_NOT_ACTIVE = ErrorCode.of(1025, "用户不存在或已被禁用");
    public static final ErrorCode PROJECT_ARCHIVED = ErrorCode.of(1026, "已归档项目不可编辑");
    public static final ErrorCode DEFAULT_PROJECT_MUST_BE_ACTIVE = ErrorCode.of(1027, "默认项目必须是活跃项目");
    public static final ErrorCode PASSWORD_WRONG = ErrorCode.of(1028, "密码错误，请重新输入");
    public static final ErrorCode INVITATION_INVALID = ErrorCode.of(1029, "邀请链接已失效");
    public static final ErrorCode INVITATION_MAX_USES = ErrorCode.of(1030, "邀请链接已达到最大使用次数");
    public static final ErrorCode INVITATION_EXPIRED = ErrorCode.of(1031, "邀请链接已过期");
    public static final ErrorCode INVITATION_REVOKED = ErrorCode.of(1032, "邀请链接已被撤销");
}
