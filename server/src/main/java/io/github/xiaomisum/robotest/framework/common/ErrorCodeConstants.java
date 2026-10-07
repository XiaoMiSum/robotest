package io.github.xiaomisum.robotest.framework.common;

import xyz.migoo.framework.common.exception.ErrorCode;

public class ErrorCodeConstants {

    // ========== 参数校验 1,000,001,001-1,000,001,010 ==========
    public static final ErrorCode VALIDATION_FAILED = ErrorCode.of(1000001001, "参数校验失败");
    public static final ErrorCode USERNAME_EXISTS = ErrorCode.of(1000001002, "用户名已存在");
    public static final ErrorCode EMAIL_EXISTS = ErrorCode.of(1000001003, "邮箱已存在");
    public static final ErrorCode WORKSPACE_NAME_EXISTS = ErrorCode.of(1000001004, "工作空间名称已存在");
    public static final ErrorCode ROLE_NAME_EXISTS = ErrorCode.of(1000001005, "角色名称已存在");
    public static final ErrorCode PASSWORD_TOO_WEAK = ErrorCode.of(1000001006, "密码强度不符合要求");
    public static final ErrorCode OLD_PASSWORD_WRONG = ErrorCode.of(1000001007, "原密码错误");
    public static final ErrorCode ROLE_TYPE_ERROR = ErrorCode.of(1000001008, "角色类型错误（只能选择系统角色）");
    public static final ErrorCode MUST_KEEP_ONE_WORKSPACE_ADMIN = ErrorCode.of(1000001009, "必须保留至少一个空间管理员");
    public static final ErrorCode USER_STATUS_INVALID = ErrorCode.of(1000001010, "用户状态不合法");

    // ========== 权限校验 1,000,002,001-1,000,002,010 ==========
    public static final ErrorCode NO_PERMISSION = ErrorCode.of(1000002001, "无权限执行此操作");
    public static final ErrorCode CANNOT_OPERATE_SELF = ErrorCode.of(1000002002, "不可操作自身账户");
    public static final ErrorCode SYSTEM_ROLE_NOT_DELETABLE = ErrorCode.of(1000002003, "系统预置角色不可删除");
    public static final ErrorCode SYSTEM_ROLE_PERMISSION_NOT_MODIFIABLE = ErrorCode.of(1000002004, "系统预置角色权限不可修改");
    public static final ErrorCode ACCOUNT_DISABLED = ErrorCode.of(1000002005, "账户已被禁用或登录凭证失效");
    public static final ErrorCode CANNOT_REMOVE_LAST_SYSTEM_ROLE = ErrorCode.of(1000002006, "不能移除自己的最后一个系统角色");
    public static final ErrorCode SYSTEM_ALREADY_INITIALIZED = ErrorCode.of(1000002007, "系统已初始化，请直接登录");
    public static final ErrorCode CONTEXT_HEADER_MISSING = ErrorCode.of(1000002008, "缺少上下文请求头，请刷新页面后重试");
    public static final ErrorCode CONTEXT_HEADER_INVALID = ErrorCode.of(1000002009, "上下文请求头格式非法");
    // 已废弃：限流统一由 migoo 框架限流承担，超限返回框架全局错误码 429（禁止新代码引用）
    @Deprecated
    public static final ErrorCode ACCESS_RATE_LIMITED = ErrorCode.of(1000002010, "请求过于频繁，请稍后再试");

    // ========== 数据不存在 1,000,003,001-1,000,003,004 ==========
    public static final ErrorCode USER_NOT_FOUND = ErrorCode.of(1000003001, "用户不存在");
    public static final ErrorCode WORKSPACE_NOT_FOUND = ErrorCode.of(1000003002, "工作空间不存在");
    public static final ErrorCode ROLE_NOT_FOUND = ErrorCode.of(1000003003, "角色不存在");

    // ========== 冲突 1,000,004,001-1,000,004,002 ==========
    public static final ErrorCode WORKSPACE_HAS_PROJECTS = ErrorCode.of(1000004001, "工作空间下存在项目，无法解散");
    public static final ErrorCode ROLE_IN_USE = ErrorCode.of(1000004002, "角色被用户引用，无法删除");

    // ========== 服务器错误 1,000,005,000 ==========
    public static final ErrorCode INTERNAL_SERVER_ERROR = ErrorCode.of(1000005000, "服务器内部错误");

    // ========== 空间管理模块 1,000,010,020-1,000,010,034 ==========
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
    public static final ErrorCode WORKSPACE_DISSOLVED = ErrorCode.of(1000010033, "工作空间已解散，不可操作");
    public static final ErrorCode WORKSPACE_NOT_DISSOLVED = ErrorCode.of(1000010034, "仅已解散的工作空间可恢复");

    // ========== 功能测试模块 1,000,011,010-1,000,011,027 ==========
    public static final ErrorCode TEST_PLAN_NOT_FOUND = ErrorCode.of(1000011010, "测试计划不存在");
    public static final ErrorCode TEST_REVIEW_NOT_FOUND = ErrorCode.of(1000011011, "评审不存在");
    public static final ErrorCode REVIEW_NOT_INITIATOR = ErrorCode.of(1000011012, "非发起人不能执行该操作");
    public static final ErrorCode PLAN_HAS_UNTESTED_CASES = ErrorCode.of(1000011013, "计划关闭时存在未执行用例");
    public static final ErrorCode NODE_VERSION_CONFLICT = ErrorCode.of(1000011014, "节点版本冲突，请刷新后重试");
    public static final ErrorCode ONLY_CASE_NODE_CAN_MARK_REVIEW = ErrorCode.of(1000011015, "只有用例节点可标记评审结果");
    public static final ErrorCode ONLY_ASSOCIATED_CASE_CAN_MARK_PLAN = ErrorCode.of(1000011016, "只有关联的用例节点可标记执行结果");
    public static final ErrorCode ROOT_NODE_NOT_EXECUTABLE = ErrorCode.of(1000011017, "默认根节点不可执行");
    public static final ErrorCode REVIEW_ONLY_ACTIVE_CAN_REJECT = ErrorCode.of(1000011018, "仅待评审或进行中的评审可驳回");
    public static final ErrorCode REVIEW_ONLY_REJECTED_CAN_REOPEN = ErrorCode.of(1000011019, "仅已驳回的评审可重新发起");
    public static final ErrorCode PLAN_ONLY_ACTIVE_CAN_BLOCK = ErrorCode.of(1000011020, "仅未开始或进行中的计划可阻塞");
    public static final ErrorCode TEST_CASE_DOCUMENT_NOT_FOUND = ErrorCode.of(1000011021, "文档不存在");
    public static final ErrorCode TEST_CASE_NODE_NOT_FOUND = ErrorCode.of(1000011022, "用例节点不存在");
    public static final ErrorCode BUG_NOT_FOUND = ErrorCode.of(1000011023, "缺陷不存在");
    public static final ErrorCode PLAN_ONLY_BLOCKED_CAN_RESUME = ErrorCode.of(1000011024, "仅已阻塞的计划可恢复");
    public static final ErrorCode PLAN_IS_BLOCKED = ErrorCode.of(1000011025, "计划已阻塞，请先恢复后再操作");
    public static final ErrorCode TEST_REVIEW_FINISHED = ErrorCode.of(1000011026, "评审已完成，无法执行该操作");
    public static final ErrorCode TEST_PLAN_FINISHED = ErrorCode.of(1000011027, "计划已结束，无法执行该操作");
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
    public static final ErrorCode API_ENV_HTTP_CONFIG_NOT_FOUND = ErrorCode.of(1000017406, "环境 HTTP 配置不存在或不属于当前环境");
    public static final ErrorCode API_ENV_DATASOURCE_NOT_FOUND = ErrorCode.of(1000017407, "环境数据源不存在或不属于当前环境");
    public static final ErrorCode API_ENV_PROCESSOR_NOT_FOUND = ErrorCode.of(1000017408, "环境处理器不存在或不属于当前环境");
    public static final ErrorCode API_ENV_VARIABLE_NOT_FOUND = ErrorCode.of(1000017409, "环境变量不存在或不属于当前环境");
    public static final ErrorCode API_ENV_VARIABLE_EXISTS = ErrorCode.of(1000017410, "变量已存在");

    // ========== 接口测试——执行引擎与快速调试 1,000,017,001-1,000,017,019（文档简写 70XX，7001 ≙ 1000017001） ==========
    public static final ErrorCode API_EXECUTOR_BUSY = ErrorCode.of(1000017001, "执行引擎繁忙，请稍后重试");
    public static final ErrorCode API_EXEC_TIMEOUT = ErrorCode.of(1000017002, "执行超时：{}");
    public static final ErrorCode API_FORMAT_CONVERT_FAILED = ErrorCode.of(1000017003, "格式转换失败：{}");
    public static final ErrorCode API_IMPORT_PARSE_FAILED = ErrorCode.of(1000017011, "导入内容解析失败：{}");
    public static final ErrorCode API_DEBUG_RECORD_NOT_FOUND = ErrorCode.of(1000017013, "调试记录不存在或不属于当前项目");

    // ========== 接口测试——接口管理 1,000,017,101-1,000,017,112（文档简写 71XX/70XX，7101 ≙ 1000017101） ==========
    public static final ErrorCode API_INTERFACE_NOT_FOUND = ErrorCode.of(1000017101, "接口定义不存在或不属于当前项目");
    public static final ErrorCode API_INTERFACE_NAME_EXISTS = ErrorCode.of(1000017102, "接口定义名称在所属模块内已存在：{}");
    public static final ErrorCode API_INTERFACE_REFERENCED = ErrorCode.of(1000017103, "接口定义被场景或 Mock 引用，禁止删除");
    public static final ErrorCode API_INTERFACE_STEP_REFERENCED = ErrorCode.of(1000017104, "公共步骤被场景链接引用，禁止删除");
    public static final ErrorCode API_INTERFACE_VERSION_CONFLICT = ErrorCode.of(1000017105, "接口已被他人修改，请刷新后重试");
    public static final ErrorCode API_IMPORT_FORMAT_UNSUPPORTED = ErrorCode.of(1000017010, "导入格式不支持：{}");
    public static final ErrorCode API_IMPORT_URL_UNREACHABLE = ErrorCode.of(1000017012, "导入目标 URL 不可达或超出超时：{}");

    // ========== 接口测试——自定义函数 1,000,017,021-1,000,017,024 ==========
    public static final ErrorCode API_CUSTOM_FUNCTION_NOT_FOUND = ErrorCode.of(1000017021, "自定义函数不存在或不属于当前可见范围");
    public static final ErrorCode API_CUSTOM_FUNCTION_NAME_CONFLICT = ErrorCode.of(1000017022, "函数名与内置函数重名或同作用域已存在同名函数：{}");
    public static final ErrorCode API_CUSTOM_FUNCTION_SCRIPT_INVALID = ErrorCode.of(1000017023, "Groovy 脚本编译失败：{}");
    public static final ErrorCode API_FUNCTION_EVAL_FAILED = ErrorCode.of(1000017024, "函数试算执行失败：{}");

    // ========== 接口测试——Mock 1,000,017,201-1,000,017,202 ==========
    public static final ErrorCode API_MOCK_NOT_FOUND = ErrorCode.of(1000017201, "Mock 定义不存在或不属于当前项目");
    public static final ErrorCode API_MOCK_ADDR_CONFLICT = ErrorCode.of(1000017202, "Mock 地址已被占用：{}");

    // ========== 接口测试——场景 1,000,017,301-1,000,017,306 ==========
    public static final ErrorCode API_SCENE_NOT_FOUND = ErrorCode.of(1000017301, "场景不存在或不属于当前项目");
    public static final ErrorCode API_SCENE_REFERENCED = ErrorCode.of(1000017302, "场景被定时任务引用，禁止删除");
    public static final ErrorCode API_SCENE_VERSION_CONFLICT = ErrorCode.of(1000017303, "场景已被他人修改，请刷新后重试");
    public static final ErrorCode API_SCENE_SETTING_INVALID = ErrorCode.of(1000017304, "场景设置项非法：{}");
    public static final ErrorCode API_SCENE_STEP_NOT_FOUND = ErrorCode.of(1000017305, "场景步骤不存在或不属于当前场景");

    // ========== 接口测试——报告与分享 1,000,017,311-1,000,017,312 ==========
    public static final ErrorCode API_REPORT_NOT_FOUND = ErrorCode.of(1000017311, "报告不存在或不属于当前项目");
    public static final ErrorCode API_SHARE_EXPIRED = ErrorCode.of(1000017312, "分享链接已过期或不存在");

    // ========== 接口测试——公共组件 1,000,017,321-1,000,017,322 ==========
    public static final ErrorCode API_COMMON_COMPONENT_NOT_FOUND = ErrorCode.of(1000017321, "公共组件不存在或不属于当前可见范围");
    public static final ErrorCode API_COMMON_COMPONENT_NAME_EXISTS = ErrorCode.of(1000017322, "同作用域下已存在同名公共组件");

    // ========== 接口测试——执行记录 1,000,017,331 ==========
    public static final ErrorCode API_EXECUTION_RECORD_NOT_FOUND = ErrorCode.of(1000017331, "执行记录不存在");

    // ========== 接口测试——定时任务 1,000,017,501-1,000,017,509 ==========
    public static final ErrorCode API_SCHEDULED_TASK_NOT_FOUND = ErrorCode.of(1000017501, "定时任务不存在或不属于当前项目");
    public static final ErrorCode API_SCHEDULED_TASK_CRON_INVALID = ErrorCode.of(1000017502, "Cron 表达式不合法：{}");
    public static final ErrorCode API_SCHEDULED_TASK_ENV_REQUIRED = ErrorCode.of(1000017503, "定时任务必须绑定执行环境");
    public static final ErrorCode API_SCHEDULED_TASK_RUNNING = ErrorCode.of(1000017504, "任务正在执行中，请等待完成后再操作");
    public static final ErrorCode API_SCHEDULED_TASK_SCENE_NOT_EXECUTABLE = ErrorCode.of(1000017505, "关联场景不存在或不可执行");
    public static final ErrorCode API_SCHEDULED_TASK_SCOPE_INVALID = ErrorCode.of(1000017506, "执行方式不合法，仅支持 all / modules / scenes");
    public static final ErrorCode API_SCHEDULED_TASK_MODULE_IDS_REQUIRED = ErrorCode.of(1000017507, "指定模块（多选）时模块列表必填");
    public static final ErrorCode API_SCHEDULED_TASK_SCENE_IDS_REQUIRED = ErrorCode.of(1000017508, "指定场景（多选）时场景列表必填");
    public static final ErrorCode API_SCHEDULED_TASK_MODULE_NOT_FOUND = ErrorCode.of(1000017509, "指定的模块不存在或不属于当前项目");
    public static final ErrorCode API_SCHEDULED_TASK_OPENAPI_URL_REQUIRED = ErrorCode.of(1000017510, "接口同步任务必须指定 OpenAPI/Swagger 文档 URL");

    // ========== 接口测试——Swagger URL 1,000,017,601-1,000,017,602 ==========
    public static final ErrorCode API_SWAGGER_URL_NOT_FOUND = ErrorCode.of(1000017601, "Swagger URL 不存在或不属于当前项目");
    public static final ErrorCode API_SWAGGER_URL_TASK_BOUND = ErrorCode.of(1000017602, "Swagger URL 被定时任务绑定无法删除，请先解除绑定");

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
    public static final ErrorCode BUG_ATTACHMENT_TYPE_NOT_ALLOWED = ErrorCode.of(1000012023, "不支持的附件类型");
    public static final ErrorCode BUG_ATTACHMENT_CONTENT_MISMATCH = ErrorCode.of(1000012024, "附件内容与文件类型不符");

    // ========== 需求管理 1,000,018,001-1,000,018,014（需求管理详设 6） ==========
    public static final ErrorCode REQUIREMENT_NOT_FOUND = ErrorCode.of(1000018001, "需求不存在或不属于当前项目");
    public static final ErrorCode REQUIREMENT_CODE_CONFLICT = ErrorCode.of(1000018002, "需求编号分配冲突（并发重试后仍失败）");
    public static final ErrorCode REQUIREMENT_STATUS_NOT_ALLOWED = ErrorCode.of(1000018003, "当前状态不允许该操作");
    public static final ErrorCode REQUIREMENT_ARCHIVED_READONLY = ErrorCode.of(1000018004, "归档条目只读");
    public static final ErrorCode REQUIREMENT_MODULE_NOT_FOUND = ErrorCode.of(1000018005, "所属模块不存在或不属于当前项目");
    public static final ErrorCode REQUIREMENT_IMPORT_TYPE_UNSUPPORTED = ErrorCode.of(1000018006, "导入文件类型不支持");
    public static final ErrorCode REQUIREMENT_IMPORT_SIZE_EXCEEDED = ErrorCode.of(1000018007, "导入文件超过 20MB 限制");
    public static final ErrorCode REQUIREMENT_IMPORT_EMPTY = ErrorCode.of(1000018008, "导入文件为空或不可解析");
    public static final ErrorCode REQUIREMENT_SPLIT_INPUT_INVALID = ErrorCode.of(1000018009, "拆分输入不满足条件（条目不可拆分）");
    public static final ErrorCode REQUIREMENT_ATTRIBUTE_INVALID = ErrorCode.of(1000018010, "需求属性取值非法");
    public static final ErrorCode REQUIREMENT_SOURCE_FILE_NOT_FOUND = ErrorCode.of(1000018011, "来源附件不存在");
    public static final ErrorCode REQUIREMENT_NO_PERMISSION = ErrorCode.of(1000018012, "无需求管理权限");
    public static final ErrorCode REQUIREMENT_TASK_IN_PROGRESS = ErrorCode.of(1000018013, "已存在进行中的导入或拆分任务");
    public static final ErrorCode REQUIREMENT_TRACE_SERVICE_FAILED = ErrorCode.of(1000018014, "追溯服务调用失败");

    // ========== 文件管理 1,000,018,021-1,000,018,039（文件管理详设 6） ==========
    public static final ErrorCode FILE_NOT_FOUND = ErrorCode.of(1000018021, "文件不存在或已删除");
    public static final ErrorCode FILE_EMPTY = ErrorCode.of(1000018022, "上传文件为空");
    public static final ErrorCode FILE_SIZE_EXCEEDED = ErrorCode.of(1000018023, "文件超过 20MB 限制");
    public static final ErrorCode FILE_TYPE_NOT_ALLOWED = ErrorCode.of(1000018024, "文件类型不允许或内容与类型不符");
    public static final ErrorCode FILE_UPLOAD_FAILED = ErrorCode.of(1000018025, "文件上传失败（对象存储不可用）");
    public static final ErrorCode FILE_DOWNLOAD_FAILED = ErrorCode.of(1000018026, "文件下载失败");
    public static final ErrorCode FILE_ACCESS_URL_FAILED = ErrorCode.of(1000018027, "文件访问地址签发失败");
    public static final ErrorCode FILE_DELETE_FAILED = ErrorCode.of(1000018028, "文件删除失败");

    // ========== AI 配置与任务引擎 1,000,018,101-1,000,018,123（AI 底座详设 6） ==========
    public static final ErrorCode AI_DISABLED = ErrorCode.of(1000018101, "AI 能力未启用（总开关关闭）");
    public static final ErrorCode AI_MODEL_NOT_FOUND = ErrorCode.of(1000018102, "模型配置不存在");
    public static final ErrorCode AI_MODEL_INVALID = ErrorCode.of(1000018103, "模型配置参数非法");
    public static final ErrorCode AI_DEFAULT_MODEL_CONFLICT = ErrorCode.of(1000018104, "默认模型引用冲突（不可删除或停用默认模型）");
    public static final ErrorCode AI_MODEL_TEST_FAILED = ErrorCode.of(1000018105, "模型连通性测试失败");
    public static final ErrorCode AI_EMBEDDING_INVALID = ErrorCode.of(1000018106, "向量 API 配置参数非法");
    public static final ErrorCode AI_EMBEDDING_TEST_FAILED = ErrorCode.of(1000018107, "向量 API 连通性测试失败");
    public static final ErrorCode AI_PROMPT_SCENE_NOT_FOUND = ErrorCode.of(1000018108, "提示词场景不存在");
    public static final ErrorCode AI_PROMPT_INVALID = ErrorCode.of(1000018109, "提示词模板校验失败（变量缺失或非法）");
    public static final ErrorCode AI_TASK_NOT_FOUND = ErrorCode.of(1000018110, "任务不存在");
    public static final ErrorCode AI_TASK_STATE_INVALID = ErrorCode.of(1000018111, "任务状态不允许该操作");
    public static final ErrorCode AI_ARTIFACT_NOT_FOUND = ErrorCode.of(1000018112, "任务产物不存在");
    public static final ErrorCode AI_ARTIFACT_ALREADY_CONFIRMED = ErrorCode.of(1000018113, "产物已确认，不可重复操作");
    public static final ErrorCode AI_TASK_TYPE_UNSUPPORTED = ErrorCode.of(1000018114, "不支持的任务类型");
    public static final ErrorCode AI_TASK_INPUT_INVALID = ErrorCode.of(1000018115, "任务输入参数非法");
    public static final ErrorCode AI_NO_PERMISSION = ErrorCode.of(1000018116, "无 AI 能力使用权限");
    public static final ErrorCode AI_MODEL_CALL_FAILED = ErrorCode.of(1000018117, "模型服务调用失败（不可用、超时或限流）");
    public static final ErrorCode AI_MODEL_NOT_CONFIGURED = ErrorCode.of(1000018118, "未配置可用模型");
    public static final ErrorCode AI_EMBEDDING_NOT_CONFIGURED = ErrorCode.of(1000018119, "向量 API 未配置或未启用，检索能力不可用");
    public static final ErrorCode AI_USAGE_RANGE_INVALID = ErrorCode.of(1000018120, "用量查询时间范围非法");
    public static final ErrorCode AI_NO_ADMIN_PERMISSION = ErrorCode.of(1000018121, "无 AI 配置管理权限");
    public static final ErrorCode AI_VECTOR_INDEX_UNAVAILABLE = ErrorCode.of(1000018122, "向量索引不可用（全量重建中）");
    public static final ErrorCode AI_WAIT_SECONDS_INVALID = ErrorCode.of(1000018123, "同步等待参数超限（0–10 秒）");

    // ========== 追溯矩阵 1,000,018,151-1,000,018,161（追溯矩阵详设 6） ==========
    public static final ErrorCode TRACE_EDGE_NOT_FOUND = ErrorCode.of(1000018151, "追溯边不存在");
    public static final ErrorCode TRACE_NODE_NOT_FOUND = ErrorCode.of(1000018152, "源或目标节点不存在");
    public static final ErrorCode TRACE_EDGE_DUPLICATE = ErrorCode.of(1000018153, "同一对节点已存在有效边");
    public static final ErrorCode TRACE_EDGE_STATE_INVALID = ErrorCode.of(1000018154, "当前边状态不允许该操作");
    public static final ErrorCode TRACE_COVERAGE_NOT_FOUND = ErrorCode.of(1000018155, "覆盖分析结论不存在");
    public static final ErrorCode TRACE_COVERAGE_INPUT_INVALID = ErrorCode.of(1000018156, "覆盖分析任务参数非法");
    public static final ErrorCode TRACE_IMPACT_INPUT_INVALID = ErrorCode.of(1000018157, "影响分析任务参数非法");
    public static final ErrorCode TRACE_IMPACT_ITEM_NOT_FOUND = ErrorCode.of(1000018158, "受影响项不存在");
    public static final ErrorCode TRACE_IMPACT_ITEM_DISPOSED = ErrorCode.of(1000018159, "受影响项已处置");
    public static final ErrorCode TRACE_NODE_TYPE_UNSUPPORTED = ErrorCode.of(1000018160, "不支持的节点类型");
    public static final ErrorCode TRACE_NO_PERMISSION = ErrorCode.of(1000018161, "无权限操作追溯数据");

    // ========== AI 生成链 1,000,018,201-1,000,018,210（生成链详设 6，号段至 1,000,018,249） ==========
    public static final ErrorCode GENERATION_REQUIREMENT_NOT_FOUND = ErrorCode.of(1000018201, "输入需求不存在");
    public static final ErrorCode GENERATION_REQUIREMENT_NOT_CONFIRMED = ErrorCode.of(1000018202, "存在非已确认需求，不可作为生成输入");
    public static final ErrorCode GENERATION_TARGET_MODULE_INVALID = ErrorCode.of(1000018203, "落位目标模块不存在或不属当前项目");
    public static final ErrorCode GENERATION_ARTIFACT_DUPLICATE = ErrorCode.of(1000018204, "产物与既有数据疑似重复，需人工处理");
    public static final ErrorCode GENERATION_INPUT_INVALID = ErrorCode.of(1000018205, "生成参数非法（粒度、条数、落位方式）");
    public static final ErrorCode SELECTION_INPUT_INVALID = ErrorCode.of(1000018206, "圈选建议输入非法（范围为空或参数越界）");
    public static final ErrorCode SELECTION_CASE_NOT_FOUND = ErrorCode.of(1000018207, "圈选用例不存在");
    public static final ErrorCode GENERATION_NO_ADOPT_PERMISSION = ErrorCode.of(1000018208, "无产物落库资源权限");
    public static final ErrorCode GENERATION_DUPLICATE_TASK = ErrorCode.of(1000018209, "已存在进行中的同输入生成任务");
    public static final ErrorCode GENERATION_PARENT_NOT_ADOPTED = ErrorCode.of(1000018210, "父级产物尚未采纳，无法落库");

    // ========== 智能助手 1000018251-1000018262（助手详设 6，号段至 1000018279） ==========
    public static final ErrorCode ASSISTANT_CONVERSATION_NOT_FOUND = ErrorCode.of(1000018251, "会话不存在或非本人会话");
    public static final ErrorCode ASSISTANT_MESSAGE_NOT_FOUND = ErrorCode.of(1000018252, "消息不存在");
    public static final ErrorCode ASSISTANT_PREVIEW_EXPIRED = ErrorCode.of(1000018253, "预览不存在或已过期");
    public static final ErrorCode ASSISTANT_PREVIEW_RESOLVED = ErrorCode.of(1000018254, "预览已执行或已取消");
    public static final ErrorCode ASSISTANT_PARSE_FAILED = ErrorCode.of(1000018255, "意图解析失败，无法生成预览");
    public static final ErrorCode ASSISTANT_SCOPE_MISMATCH = ErrorCode.of(1000018256, "执行作用域与预览目标不一致");
    public static final ErrorCode ASSISTANT_NO_PERMISSION = ErrorCode.of(1000018257, "无权限执行该操作");
    public static final ErrorCode ASSISTANT_RAG_UNAVAILABLE = ErrorCode.of(1000018258, "只读问答检索不可用（向量能力未就绪）");
    public static final ErrorCode ASSISTANT_CONVERSATION_ARCHIVED = ErrorCode.of(1000018259, "会话已归档，不可发送消息");
    public static final ErrorCode ASSISTANT_CONTENT_INVALID = ErrorCode.of(1000018260, "消息内容为空或超过长度限制");
    public static final ErrorCode ASSISTANT_CROSS_WORKSPACE = ErrorCode.of(1000018261, "跨工作空间指代被拒绝，请切换作用域");
    public static final ErrorCode ASSISTANT_ATTACHMENT_INVALID = ErrorCode.of(1000018262, "附件引用非法");
}
