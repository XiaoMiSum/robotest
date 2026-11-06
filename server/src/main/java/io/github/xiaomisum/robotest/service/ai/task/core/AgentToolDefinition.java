package io.github.xiaomisum.robotest.service.ai.task.core;

import java.util.Map;

/**
 * 任务侧工具定义（总册 2.6.5）：服务端注册与模型声明共用同一份定义，
 * paramSchema 为 OpenAI tools 参数 JSON Schema。
 *
 * @param code           工具标识（= ai_task_tool.tool_code，模型按此调用）
 * @param name           展示名（轨迹与确认闸门用）
 * @param description    供模型理解的用途与调用时机描述
 * @param paramsSchema   参数 JSON Schema
 * @param permissionCode 调用所需权限点（null = 继承发起人权限），执行期服务端鉴权
 * @param sideEffect     readonly / confirmation（写库与出网副作用禁止注册）
 */
public record AgentToolDefinition(String code, String name, String description,
        Map<String, Object> paramsSchema, String permissionCode, String sideEffect) {
}
