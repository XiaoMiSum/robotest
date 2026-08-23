package io.github.xiaomisum.robotest.framework.common;

import xyz.migoo.framework.common.exception.ErrorCode;

public class ErrorCodeConstants {

    // ========== 参数校验 1,000,001,001-1,000,001,009 ==========
    public static final ErrorCode VALIDATION_FAILED = ErrorCode.of(1000001001, "参数校验失败");
    public static final ErrorCode USERNAME_EXISTS = ErrorCode.of(1000001002, "用户名已存在");
    public static final ErrorCode EMAIL_EXISTS = ErrorCode.of(1000001003, "邮箱已存在");
    public static final ErrorCode WORKSPACE_NAME_EXISTS = ErrorCode.of(1000001004, "工作空间名称已存在");
    public static final ErrorCode ROLE_NAME_EXISTS = ErrorCode.of(1000001005, "角色名称已存在");
    public static final ErrorCode PASSWORD_TOO_WEAK = ErrorCode.of(1000001006, "密码强度不符合要求");
    public static final ErrorCode OLD_PASSWORD_WRONG = ErrorCode.of(1000001007, "原密码错误");
    public static final ErrorCode ROLE_TYPE_ERROR = ErrorCode.of(1000001008, "角色类型错误（只能选择系统角色）");
    public static final ErrorCode MUST_KEEP_ONE_WORKSPACE_ADMIN = ErrorCode.of(1000001009, "必须保留至少一个空间管理员");

    // ========== 权限校验 1,000,002,001-1,000,002,006 ==========
    public static final ErrorCode NO_PERMISSION = ErrorCode.of(1000002001, "无权限执行此操作");
    public static final ErrorCode CANNOT_OPERATE_SELF = ErrorCode.of(1000002002, "不可操作自身账户");
    public static final ErrorCode SYSTEM_ROLE_NOT_DELETABLE = ErrorCode.of(1000002003, "系统预置角色不可删除");
    public static final ErrorCode SYSTEM_ROLE_PERMISSION_NOT_MODIFIABLE = ErrorCode.of(1000002004, "系统预置角色权限不可修改");
    public static final ErrorCode ACCOUNT_DISABLED = ErrorCode.of(1000002005, "账户已被禁用或登录凭证失效");
    public static final ErrorCode CANNOT_REMOVE_LAST_SYSTEM_ROLE = ErrorCode.of(1000002006, "不能移除自己的最后一个系统角色");
    public static final ErrorCode SYSTEM_ALREADY_INITIALIZED = ErrorCode.of(1000002007, "系统已初始化，请直接登录");

    // ========== 数据不存在 1,000,003,001-1,000,003,004 ==========
    public static final ErrorCode USER_NOT_FOUND = ErrorCode.of(1000003001, "用户不存在");
    public static final ErrorCode WORKSPACE_NOT_FOUND = ErrorCode.of(1000003002, "工作空间不存在");
    public static final ErrorCode ROLE_NOT_FOUND = ErrorCode.of(1000003003, "角色不存在");
    public static final ErrorCode AI_TASK_NOT_FOUND = ErrorCode.of(1000003004, "任务不存在或不属于当前项目");
    public static final ErrorCode AI_CONVERSATION_NOT_FOUND = ErrorCode.of(1000003005, "会话不存在");

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

    // ========== 功能测试模块 1,000,011,010-1,000,011,028 ==========
    public static final ErrorCode TEST_PLAN_NOT_FOUND = ErrorCode.of(1000011010, "测试计划不存在");
    public static final ErrorCode TEST_REVIEW_NOT_FOUND = ErrorCode.of(1000011011, "评审不存在");
    public static final ErrorCode REVIEW_NOT_INITIATOR = ErrorCode.of(1000011012, "非发起人不能执行该操作");
    public static final ErrorCode PLAN_HAS_UNTESTED_CASES = ErrorCode.of(1000011013, "计划关闭时存在未执行用例");
    public static final ErrorCode NODE_VERSION_CONFLICT = ErrorCode.of(1000011014, "节点版本冲突，请刷新后重试");
    public static final ErrorCode ONLY_CASE_NODE_CAN_MARK_REVIEW = ErrorCode.of(1000011015, "只有用例节点可标记评审结果");
    public static final ErrorCode ONLY_ASSOCIATED_CASE_CAN_MARK_PLAN = ErrorCode.of(1000011016, "只有关联的用例节点可标记执行结果");
    public static final ErrorCode ROOT_NODE_NOT_EXECUTABLE = ErrorCode.of(1000011017, "默认根节点不可执行");
    public static final ErrorCode TEST_CASE_DOCUMENT_NOT_FOUND = ErrorCode.of(1000011021, "文档不存在");
    public static final ErrorCode TEST_CASE_NODE_NOT_FOUND = ErrorCode.of(1000011022, "用例节点不存在");
    public static final ErrorCode BUG_NOT_FOUND = ErrorCode.of(1000011023, "缺陷不存在");
    public static final ErrorCode TEST_REVIEW_FINISHED = ErrorCode.of(1000011026, "评审已完成，无法执行该操作");
    public static final ErrorCode TEST_PLAN_FINISHED = ErrorCode.of(1000011027, "计划已结束，无法执行该操作");
    public static final ErrorCode REQUIREMENT_NOT_FOUND = ErrorCode.of(1000011028, "需求条目不存在或不属于当前项目");

    // ========== 项目模块管理 1,000,017,051-1,000,017,059（文档简写 70XX） ==========
    public static final ErrorCode PROJECT_MODULE_NOT_FOUND = ErrorCode.of(1000017051, "模块不存在");
    public static final ErrorCode PROJECT_MODULE_NAME_EXISTS = ErrorCode.of(1000017052, "同级模块名称已存在");
    public static final ErrorCode PROJECT_MODULE_MOVE_TARGET_INVALID = ErrorCode.of(1000017053, "移动目标不存在或不是目录");
    public static final ErrorCode PROJECT_MODULE_MOVE_CYCLE = ErrorCode.of(1000017054, "不能移动到自身或其子级目录下");
    public static final ErrorCode PROJECT_MODULE_NOT_EMPTY = ErrorCode.of(1000017055, "模块非空（含子模块或用例文档），无法删除");

    // ========== 用例文档管理 1,000,017,061-1,000,017,069 ==========
    public static final ErrorCode TEST_CASE_DOCUMENT_NAME_EXISTS = ErrorCode.of(1000017061, "同模块下用例名称已存在");

    // ========== 接口测试——环境管理 1,000,017,401-1,000,017,409（文档简写 74XX，7401 ≙ 1000017401） ==========
    public static final ErrorCode API_ENV_NAME_EXISTS = ErrorCode.of(1000017401, "环境名称重复");
    public static final ErrorCode API_ENV_REFERENCED = ErrorCode.of(1000017402, "环境被场景引用无法删除，请先解除引用");
    public static final ErrorCode API_DATASOURCE_CONN_FAILED = ErrorCode.of(1000017403, "数据源连接测试失败：{}");
    public static final ErrorCode API_ENV_TASK_BOUND = ErrorCode.of(1000017404, "环境被定时任务绑定无法删除，请先解除绑定");
    public static final ErrorCode API_ENV_NOT_FOUND = ErrorCode.of(1000017405, "环境不存在或不属于当前项目");

    // ========== 项目设置 1,000,017,701-1,000,017,702（文档简写 77XX，7701 ≙ 1000017701） ==========
    public static final ErrorCode API_SETTING_KEY_INVALID = ErrorCode.of(1000017701, "设置项标识非法（不在注册表白名单）");
    public static final ErrorCode API_SETTING_VALUE_INVALID = ErrorCode.of(1000017702, "设置值非法（格式或取值范围不满足注册表约束）：{}");

    // ========== 缺陷管理模块 1,000,012,001-1,000,012,010 ==========
    public static final ErrorCode BUG_INVALID_STATUS_TRANSITION = ErrorCode.of(1000012001, "缺陷状态流转不合法");
    public static final ErrorCode BUG_ALREADY_CLOSED = ErrorCode.of(1000012002, "缺陷已关闭，不可再修改状态");
    public static final ErrorCode BUG_REOPEN_COMMENT_REQUIRED = ErrorCode.of(1000012003, "重开缺陷时必须填写说明");
    public static final ErrorCode BUG_CLOSE_COMMENT_REQUIRED = ErrorCode.of(1000012004, "关闭缺陷时必须填写关闭说明");
    public static final ErrorCode BUG_ASSIGNEE_NOT_IN_WORKSPACE = ErrorCode.of(1000012005, "处理人不在当前工作空间中");
    public static final ErrorCode PROJECT_NOT_ACTIVE = ErrorCode.of(1000012006, "项目已归档，不可操作");
    public static final ErrorCode BUG_ATTACHMENT_NOT_FOUND = ErrorCode.of(1000012007, "附件不存在");
    public static final ErrorCode BUG_ATTACHMENT_SIZE_EXCEEDED = ErrorCode.of(1000012008, "附件大小超过限制");
    public static final ErrorCode BUG_CLOSED_ATTACHMENT_FORBIDDEN = ErrorCode.of(1000012009, "缺陷已关闭，不可操作附件");
    public static final ErrorCode BUG_ATTACHMENT_STORE_FAILED = ErrorCode.of(1000012010, "附件存储失败");
    public static final ErrorCode BUG_RESOLUTION_REQUIRED = ErrorCode.of(1000012011, "解决缺陷时必须选择解决方案");
    public static final ErrorCode BUG_RESOLUTION_INVALID = ErrorCode.of(1000012012, "解决方案不合法");
    public static final ErrorCode BUG_DUPLICATE_OF_REQUIRED = ErrorCode.of(1000012013, "解决方案为重复缺陷时必须指定原始缺陷");
    public static final ErrorCode BUG_DUPLICATE_OF_NOT_FOUND = ErrorCode.of(1000012014, "指定的原始缺陷不存在或不合法");
    public static final ErrorCode BUG_MODULE_NOT_FOUND = ErrorCode.of(1000012015, "所属模块不存在或不属于当前项目");
    public static final ErrorCode BUG_ALREADY_CONFIRMED = ErrorCode.of(1000012016, "缺陷已确认，无需重复确认");
    public static final ErrorCode BUG_CONFIRM_INVALID_STATUS = ErrorCode.of(1000012017, "仅激活状态的缺陷可确认");
    public static final ErrorCode BUG_TYPE_INVALID = ErrorCode.of(1000012018, "缺陷类型不合法");
    public static final ErrorCode BUG_CLOSED_EDIT_FORBIDDEN = ErrorCode.of(1000012019, "缺陷已关闭，不可编辑");
    public static final ErrorCode BUG_RESOLVE_COMMENT_REQUIRED = ErrorCode.of(1000012020, "解决缺陷时必须填写备注说明");
    public static final ErrorCode BUG_REJECT_COMMENT_REQUIRED = ErrorCode.of(1000012021, "拒绝缺陷时必须填写说明");
    public static final ErrorCode BUG_RELATION_INVALID = ErrorCode.of(1000012022, "关联用例或计划标识不合法");

    // ========== AI 能力域 1,000,013,001-1,000,013,099（文档简写 60XX，6001 ≙ 1000013001）
    // ==========
    public static final ErrorCode AI_NOT_ENABLED = ErrorCode.of(1000013001, "AI 功能未启用或配置缺失");
    public static final ErrorCode AI_CALL_FAILED = ErrorCode.of(1000013002, "AI 调用失败");
    public static final ErrorCode AI_OUTPUT_SCHEMA_INVALID = ErrorCode.of(1000013003, "AI 输出结构化校验失败");
    public static final ErrorCode AI_RATE_LIMITED = ErrorCode.of(1000013004, "AI 调用频率超限");
    public static final ErrorCode AI_TASK_DUPLICATE = ErrorCode.of(1000013005, "已存在进行中的同类任务");
    public static final ErrorCode AI_TASK_STATE_INVALID = ErrorCode.of(1000013006, "任务不存在或当前状态不允许该操作");
    public static final ErrorCode AI_CONNECTIVITY_TEST_FAILED = ErrorCode.of(1000013007, "连通性测试失败");
    public static final ErrorCode AI_EMBEDDING_DIMENSION_INVALID = ErrorCode.of(1000013008, "Embedding 维度校验失败");
    public static final ErrorCode AI_PROMPT_TEMPLATE_INVALID = ErrorCode.of(1000013009, "提示词模板校验失败");
    public static final ErrorCode AI_SEMANTIC_DEGRADED = ErrorCode.of(1000013010, "语义检索能力降级中");
    public static final ErrorCode AI_ASSISTANT_CONFIRM_TOKEN_INVALID = ErrorCode.of(1000013011, "写操作确认令牌不存在或已失效");
    public static final ErrorCode AI_TARGET_STATE_INVALID = ErrorCode.of(1000013012, "目标对象状态不允许该 AI 操作");
    public static final ErrorCode AI_PROMPT_TEMPLATE_NOT_FOUND = ErrorCode.of(1000013013, "提示词模板未配置，请检查初始化脚本或联系管理员恢复默认");
}
