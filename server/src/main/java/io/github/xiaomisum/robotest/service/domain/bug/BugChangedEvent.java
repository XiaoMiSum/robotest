package io.github.xiaomisum.robotest.service.domain.bug;

import java.util.UUID;

/**
 * 缺陷变更领域事件：缺陷域只发布、不感知 AI 实现，消费端在 service.ai 内部订阅（06 §3.1.1）
 */
public record BugChangedEvent(UUID bugId, BugChangeOp op) {
}