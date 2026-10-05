package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;

/**
 * 生成链产物采纳服务（生成链详设 3.5）：模块 / 脑图文档 / 用例节点三类产物的落库编排，
 * 含目标模块解析、父级已采纳约束与 AI 派生追溯边。
 */
public interface GenerationAdoptService {

    /** 驳回返回 null（仅记录确认动作），采纳返回落库实体与 adopted_ref */
    AdoptOutcome adopt(AdoptContext context);
}
