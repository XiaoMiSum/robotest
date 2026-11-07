package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 提案采纳上下文：落库目标项目经 X-Active-Project 头解析后传入，不出请求体（C4，总册 3.6.5）；
 * proposal 为本次确认的提案行（结构化内容取 {@code proposal.getContent()}，键取 proposalKey）；
 * allProposalKeys 为最新一次执行的提案键全集（拆解记录「全部处置完毕」判定用）；
 * loginUser 随请求带入，供承接服务做落库资源权限判定（1000018208）；
 * createParams 为圈选确认的既有创建流程参数（生成链详设 3.6「调整后采纳」）；
 * targetDocumentId / extraNodePosition / targetPlanId / round 为辅助功能三类型的落位提示，
 * 缺省时由承接服务回退任务入参（任务详情页发起不带 target）。
 */
public record AdoptContext(AiTask task, AiTaskProposal proposal, String action,
                           Map<String, Object> content, String note, UUID targetModuleId, String position,
                           String targetSystemVersion, Map<String, Object> createParams,
                           UUID targetDocumentId, String extraNodePosition, UUID targetPlanId, Integer round,
                           UUID projectId, UUID operatorId, Set<String> allProposalKeys, LoginUser loginUser) {

    /** 提案结构化内容（原 artifact.content 口径：结构化产物 + 证据链） */
    public Map<String, Object> proposalContent() {
        Map<String, Object> value = proposal == null ? null : proposal.getContent();
        return value == null ? Map.of() : value;
    }
}
