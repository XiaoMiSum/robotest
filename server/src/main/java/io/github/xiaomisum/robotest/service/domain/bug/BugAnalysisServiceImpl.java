package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 缺陷相似检索实现（详设 3.4）：新建建议内部的同步向量检索，仅做项目内过滤与排序建议，
 * 能力不可用时静默降级为空，不单独对外暴露。
 */
@Service
public class BugAnalysisServiceImpl implements BugAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(BugAnalysisServiceImpl.class);

    private static final int DUPLICATE_LIMIT = 5;
    private static final int BASIS_MAX_LENGTH = 200;
    /** 比率精度：四位小数，避免展示层出现无意义尾数 */
    private static final int RATE_SCALE = 10_000;

    @Resource
    private BugMapper bugMapper;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public List<BugDraftSuggestRespDTO.DuplicateItem> findDuplicates(String title, String steps,
            UUID projectId, UUID userId) {
        String queryText = StringUtils.hasText(steps) ? title + "\n" + steps : title;
        List<AiVectorSearchHitRespDTO> hits;
        try {
            hits = vectorSearchService.search(queryText, List.of(projectId),
                    VectorIndexService.TYPE_BUG, DUPLICATE_LIMIT, userId);
        } catch (Exception e) {
            // 向量未就绪 / 检索失败只影响重复检测部分，静默降级不阻断字段建议（详设 3.4）
            log.warn("[AI] 缺陷相似检索不可用 projectId={}", projectId, e);
            return List.of();
        }

        List<UUID> hitIds = hits.stream()
                .map(AiVectorSearchHitRespDTO::getEntityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Bug> bugById = hitIds.isEmpty() ? Map.of()
                : bugMapper.listByIds(hitIds).stream()
                        .filter(bug -> projectId.equals(bug.getProjectId()))
                        .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));

        // 同一缺陷多分块命中只回首条（检索已按相似度排序）；已删除缺陷由 listByIds 过滤
        Set<UUID> seen = new LinkedHashSet<>();
        List<BugDraftSuggestRespDTO.DuplicateItem> items = new ArrayList<>();
        for (AiVectorSearchHitRespDTO hit : hits) {
            UUID bugId = hit.getEntityId();
            if (bugId == null || !seen.add(bugId)) {
                continue;
            }
            Bug bug = bugById.get(bugId);
            if (bug == null) {
                continue;
            }
            BugDraftSuggestRespDTO.DuplicateItem item = new BugDraftSuggestRespDTO.DuplicateItem();
            item.setBugId(bug.getId());
            item.setTitle(bug.getTitle());
            item.setStatus(bug.getStatus());
            // similarity 由距离单调换算，仅作排序展示，阈值不对外承诺（详设 3.4）
            item.setSimilarity(hit.getDistance() == null ? null
                    : Math.round((1 - hit.getDistance()) * RATE_SCALE) / (double) RATE_SCALE);
            item.setBasis(basisOf(hit.getContent()));
            items.add(item);
            if (items.size() >= DUPLICATE_LIMIT) {
                break;
            }
        }
        return items;
    }

    private static String basisOf(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= BASIS_MAX_LENGTH ? trimmed : trimmed.substring(0, BASIS_MAX_LENGTH) + "…";
    }
}
