package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;

/**
 * 存量重复扫描确认承接（缺陷分析详设 3.9）：只写确认留痕，不修改任何缺陷——
 * 确认记录（ai_artifact_confirm，含 action / note）由总册确认入口内建，此处仅回执分组结论；
 * 确认后由人工在缺陷中按既有「重复缺陷」解决方案逐条处理。
 */
@Service
public class BugDuplicateScanAdopter implements ArtifactAdopter {

    @Override
    public String type() {
        return BugDuplicateScanHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        Map<String, Object> content = context.content() != null ? context.content()
                : asMap(asMap(context.artifact()).get("content"));
        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("canonicalBugId", content.get("canonicalBugId"));
        adoptedRef.put("itemCount", content.get("items") instanceof List<?> items ? items.size() : 0);
        return new AdoptOutcome(null, adoptedRef);
    }
}
