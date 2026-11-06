package io.github.xiaomisum.robotest.service.ai.task.type;

import java.util.Map;

/**
 * 提案草稿（总册 2.6.4）：类型适配器的执行终态产出，由执行核落库为 ai_task_proposal 行。
 *
 * @param proposalKey 提案在任务产出中的定位键（任务内唯一，适配器按域语义生成）
 * @param kind        提案类型（取值见各分册，与旧 AiArtifactKind 一致）
 * @param parentKey   树形提案的父提案键，非树形为 null
 * @param title       提案标题
 * @param content     结构化产物 + 证据链（结构由各分册定义）
 */
public record ProposalDraft(String proposalKey, String kind, String parentKey, String title,
        Map<String, Object> content) {
}
