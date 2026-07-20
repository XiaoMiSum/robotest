package io.github.xiaomisum.robotest.common;

import xyz.migoo.framework.common.exception.ErrorCode;

public class ErrorCodeConstants {

    // ========== 预置工作空间角色 ID（与 V5 迁移脚本一致） ==========
    public static final String WORKSPACE_ROLE_ADMIN_ID  = "c0000000-0000-0000-0000-000000000001";
    public static final String WORKSPACE_ROLE_MEMBER_ID = "c0000000-0000-0000-0000-000000000002";

    // ========== 参数校验 1,000,001,001-1,000,001,009 ==========
    public static final ErrorCode VALIDATION_FAILED = ErrorCode.of(1000001001, "参数校验失败");
    public static final ErrorCode USERNAME_EXISTS = ErrorCode.of(1000001002, "用户名已存在");
    public static final ErrorCode EMAIL_EXISTS = ErrorCode.of(1000001003, "邮箱已存在");
    public static final ErrorCode WORKSPACE_NAME_EXISTS = ErrorCode.of(1000001004, "工作空间名称已存在");
    public static final ErrorCode ROLE_NAME_EXISTS = ErrorCode.of(1000001005, "角色名称已存在");
    public static final ErrorCode PASSWORD_TOO_WEAK = ErrorCode.of(1000001006, "密码强度不符合要求");
    public static final ErrorCode ROLE_TYPE_ERROR = ErrorCode.of(1000001008, "角色类型错误（只能选择系统角色）");
    public static final ErrorCode MUST_KEEP_ONE_WORKSPACE_ADMIN = ErrorCode.of(1000001009, "必须保留至少一个空间管理员");

    // ========== 权限校验 1,000,002,001-1,000,002,006 ==========
    public static final ErrorCode NO_PERMISSION = ErrorCode.of(1000002001, "无权限执行此操作");
    public static final ErrorCode CANNOT_OPERATE_SELF = ErrorCode.of(1000002002, "不可操作自身账户");
    public static final ErrorCode SYSTEM_ROLE_NOT_DELETABLE = ErrorCode.of(1000002003, "系统预置角色不可删除");
    public static final ErrorCode SYSTEM_ROLE_PERMISSION_NOT_MODIFIABLE = ErrorCode.of(1000002004, "系统预置角色权限不可修改");
    public static final ErrorCode ACCOUNT_DISABLED = ErrorCode.of(1000002005, "账户已被禁用或登录凭证失效");
    public static final ErrorCode CANNOT_REMOVE_LAST_SYSTEM_ROLE = ErrorCode.of(1000002006, "不能移除自己的最后一个系统角色");

    // ========== 数据不存在 1,000,003,001-1,000,003,003 ==========
    public static final ErrorCode USER_NOT_FOUND = ErrorCode.of(1000003001, "用户不存在");
    public static final ErrorCode WORKSPACE_NOT_FOUND = ErrorCode.of(1000003002, "工作空间不存在");
    public static final ErrorCode ROLE_NOT_FOUND = ErrorCode.of(1000003003, "角色不存在");

    // ========== 冲突 1,000,004,001-1,000,004,002 ==========
    public static final ErrorCode WORKSPACE_HAS_PROJECTS = ErrorCode.of(1000004001, "工作空间下存在项目，无法解散");
    public static final ErrorCode ROLE_IN_USE = ErrorCode.of(1000004002, "角色被用户引用，无法删除");

    // ========== 服务器错误 1,000,005,000 ==========
    public static final ErrorCode INTERNAL_SERVER_ERROR = ErrorCode.of(1000005000, "服务器内部错误");

    // ========== 空间管理模块 1,000,010,020-1,000,010,032 ==========
    public static final ErrorCode PROJECT_NAME_EXISTS = ErrorCode.of(1000010020, "项目名称在当前工作空间已存在");
    public static final ErrorCode PROJECT_NOT_FOUND = ErrorCode.of(1000010021, "项目不存在或不属于当前工作空间");
    public static final ErrorCode PROJECT_HAS_ACTIVE_PLANS = ErrorCode.of(1000010022, "项目下存在进行中的测试计划，无法归档");
    public static final ErrorCode PROJECT_HAS_DATA = ErrorCode.of(1000010023, "项目下存在数据，无法删除");
    public static final ErrorCode USER_ALREADY_IN_WORKSPACE = ErrorCode.of(1000010024, "用户已在工作空间中");
    public static final ErrorCode USER_NOT_ACTIVE = ErrorCode.of(1000010025, "用户不存在或已被禁用");
    public static final ErrorCode PROJECT_ARCHIVED = ErrorCode.of(1000010026, "已归档项目不可编辑");
    public static final ErrorCode DEFAULT_PROJECT_MUST_BE_ACTIVE = ErrorCode.of(1000010027, "默认项目必须是活跃项目");
    public static final ErrorCode PASSWORD_WRONG = ErrorCode.of(1000010028, "密码错误，请重新输入");
    public static final ErrorCode INVITATION_INVALID = ErrorCode.of(1000010029, "邀请链接已失效");
    public static final ErrorCode INVITATION_MAX_USES = ErrorCode.of(1000010030, "邀请链接已达到最大使用次数");
    public static final ErrorCode INVITATION_EXPIRED = ErrorCode.of(1000010031, "邀请链接已过期");
    public static final ErrorCode INVITATION_REVOKED = ErrorCode.of(1000010032, "邀请链接已被撤销");

    // ========== 功能测试模块 1,000,011,001-1,000,011,017 ==========
    public static final ErrorCode TEST_PLAN_NOT_FOUND = ErrorCode.of(1000011010, "测试计划不存在");
    public static final ErrorCode TEST_REVIEW_NOT_FOUND = ErrorCode.of(1000011011, "评审不存在");
    public static final ErrorCode REVIEW_NOT_INITIATOR = ErrorCode.of(1000011012, "非发起人不能完成评审");
    public static final ErrorCode PLAN_HAS_UNTESTED_CASES = ErrorCode.of(1000011013, "计划关闭时存在未执行用例");
    public static final ErrorCode NODE_VERSION_CONFLICT = ErrorCode.of(1000011014, "节点版本冲突，请刷新后重试");
    public static final ErrorCode ONLY_CASE_NODE_CAN_MARK_REVIEW = ErrorCode.of(1000011015, "只有用例节点可标记评审结果");
    public static final ErrorCode ONLY_ASSOCIATED_CASE_CAN_MARK_PLAN = ErrorCode.of(1000011016, "只有关联的用例节点可标记执行结果");
    public static final ErrorCode ROOT_NODE_NOT_EXECUTABLE = ErrorCode.of(1000011017, "默认根节点不可执行");
    public static final ErrorCode TEST_CASE_MODULE_NOT_FOUND = ErrorCode.of(1000011018, "模块不存在");
    public static final ErrorCode TEST_CASE_MODULE_NAME_EXISTS = ErrorCode.of(1000011019, "同级模块名称已存在");
    public static final ErrorCode TEST_CASE_MODULE_NOT_EMPTY = ErrorCode.of(1000011020, "目录不为空，无法删除");
    public static final ErrorCode TEST_CASE_DOCUMENT_NOT_FOUND = ErrorCode.of(1000011021, "文档不存在");
    public static final ErrorCode TEST_CASE_NODE_NOT_FOUND = ErrorCode.of(1000011022, "用例节点不存在");
    public static final ErrorCode BUG_NOT_FOUND = ErrorCode.of(1000011023, "缺陷不存在");
}
