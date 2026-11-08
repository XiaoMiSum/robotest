package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;

/** 域适配支撑产物到通用提案草稿的无副作用转换。 */
public final class ProposalDrafts {
    private ProposalDrafts() { }

    public static List<ProposalDraft> fromArtifacts(List<Map<String, Object>> artifacts) {
        List<ProposalDraft> result = new ArrayList<>();
        for (Map<String, Object> artifact : artifacts == null ? List.<Map<String, Object>>of() : artifacts) {
            String status = Constants.AiProposalConfirmStatus.NOT_APPLICABLE.equals(artifact.get("confirmStatus"))
                    ? Constants.AiProposalConfirmStatus.NOT_APPLICABLE : Constants.AiProposalConfirmStatus.PENDING;
            result.add(new ProposalDraft(text(artifact.get("key")), text(artifact.get("kind")),
                    text(artifact.get("parentKey")), text(artifact.get("title")), map(artifact.get("content")), status));
        }
        return result;
    }

    public static List<ProposalDraft> fromArtifact(Map<String, Object> artifact) {
        return fromArtifacts(List.of(artifact));
    }

    public static List<ProposalDraft> parse(String output, String defaultKind) {
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(output);
        Object raw = parsed.get("proposals") instanceof List<?> ? parsed.get("proposals") : parsed.get("artifacts");
        if (!(raw instanceof List<?> values)) return List.of();
        List<Map<String, Object>> artifacts = new ArrayList<>();
        int seq = 0;
        for (Object value : values) {
            Map<String, Object> item = map(value);
            if (item.isEmpty()) continue;
            seq++;
            item.putIfAbsent("key", "item-" + seq);
            item.putIfAbsent("kind", defaultKind);
            item.putIfAbsent("title", "AI 建议 " + seq);
            artifacts.add(item);
        }
        return fromArtifacts(artifacts);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> ? new LinkedHashMap<>((Map<String, Object>) raw) : Map.of();
    }

    private static String text(Object raw) { return raw == null ? null : String.valueOf(raw); }
}
