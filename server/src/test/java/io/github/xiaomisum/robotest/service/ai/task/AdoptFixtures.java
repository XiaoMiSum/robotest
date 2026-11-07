package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S3 提案表切换后的承接上下文测试夹具：旧产物 Map（key / kind / title / parentKey / content）
 * → 提案行；执行 ID 固定，供按执行命名空间的采纳记录查询打桩。
 */
public final class AdoptFixtures {

    /** 夹具执行 ID：承接实现按 (execution_id, proposal_key) 查采纳记录 */
    public static final UUID EXECUTION_ID = UUID.randomUUID();

    private AdoptFixtures() {
    }

    /** 产物 Map → 提案行；content 缺失时给空表（与旧 contentOf 回落口径一致） */
    @SuppressWarnings("unchecked")
    public static AiTaskProposal proposal(Map<String, Object> artifact) {
        AiTaskProposal proposal = new AiTaskProposal();
        proposal.setExecutionId(EXECUTION_ID);
        proposal.setProposalKey(asString(artifact.get("key")));
        proposal.setKind(asString(artifact.get("kind")));
        proposal.setParentKey(asString(artifact.get("parentKey")));
        proposal.setTitle(asString(artifact.get("title")));
        Object content = artifact.get("content");
        proposal.setContent(content instanceof Map ? (Map<String, Object>) content : new LinkedHashMap<>());
        proposal.setConfirmStatus("pending");
        return proposal;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
