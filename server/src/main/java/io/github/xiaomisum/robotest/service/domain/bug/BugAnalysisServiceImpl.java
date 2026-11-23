package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDuplicateCheckReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDuplicateCheckRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 缺陷分析实现（详设 3.6）：录入时重复检测走同步向量检索，仅做项目内过滤与排序建议，不落中间表。
 */
@Service
public class BugAnalysisServiceImpl implements BugAnalysisService {

    private static final int TITLE_MAX_LENGTH = 300;
    private static final int DEFAULT_DUPLICATE_LIMIT = 5;
    private static final int MAX_DUPLICATE_LIMIT = 20;
    private static final int BASIS_MAX_LENGTH = 200;
    /** 比率精度：四位小数，避免展示层出现无意义尾数 */
    private static final int RATE_SCALE = 10_000;

    @Resource
    private BugMapper bugMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public BugDuplicateCheckRespDTO checkDuplicates(BugDuplicateCheckReqDTO reqDTO, UUID projectId, UUID userId) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        String title = reqDTO.getTitle() == null ? "" : reqDTO.getTitle().trim();
        if (title.isEmpty() || title.length() > TITLE_MAX_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID);
        }
        int limit = reqDTO.getLimit() == null ? DEFAULT_DUPLICATE_LIMIT : reqDTO.getLimit();
        if (limit < 1 || limit > MAX_DUPLICATE_LIMIT) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_CHECK_INPUT_INVALID);
        }
        String queryText = StringUtils.hasText(reqDTO.getSteps()) ? title + "\n" + reqDTO.getSteps() : title;

        List<AiVectorSearchHitRespDTO> hits;
        try {
            hits = vectorSearchService.search(queryText, List.of(projectId), VectorIndexService.TYPE_BUG,
                    limit, userId);
        } catch (ServiceException e) {
            // 未配置 / 重建中按向量能力未就绪口径回执，入口据此静默降级（详设 3.6）
            if (e.getCode() == ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code()
                    || e.getCode() == ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE.code()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_RAG_UNAVAILABLE);
            }
            throw e;
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

        // 同一缺陷多分块命中只回首条（检索已按相似度排序）；已删除缺陷跳过
        Set<UUID> seen = new LinkedHashSet<>();
        List<BugDuplicateCheckRespDTO.Item> items = new ArrayList<>();
        for (AiVectorSearchHitRespDTO hit : hits) {
            UUID bugId = hit.getEntityId();
            if (bugId == null || !seen.add(bugId)) {
                continue;
            }
            Bug bug = bugById.get(bugId);
            if (bug == null) {
                continue;
            }
            BugDuplicateCheckRespDTO.Item item = new BugDuplicateCheckRespDTO.Item();
            item.setBugId(bug.getId());
            item.setTitle(bug.getTitle());
            item.setStatus(bug.getStatus());
            // similarity 由距离单调换算，仅作排序展示，阈值不对外承诺（详设 3.6）
            item.setSimilarity(hit.getDistance() == null ? null
                    : Math.round((1 - hit.getDistance()) * RATE_SCALE) / (double) RATE_SCALE);
            item.setBasis(basisOf(hit.getContent()));
            items.add(item);
        }

        BugDuplicateCheckRespDTO resp = new BugDuplicateCheckRespDTO();
        resp.setList(items);
        return resp;
    }

    private static String basisOf(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= BASIS_MAX_LENGTH ? trimmed : trimmed.substring(0, BASIS_MAX_LENGTH) + "…";
    }
}
