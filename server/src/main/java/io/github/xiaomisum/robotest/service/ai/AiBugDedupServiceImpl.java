package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugDedupReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugDedupRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.AiVectorSearchService.BugDedupHit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 缺陷语义查重实现（3.2）：Embedding 可用且调用成功返回语义相似度结果；
 * 未配置/降级状态/调用异常时自动降级为关键词匹配（semanticDegraded=true，similarity=null），
 * 不抛错打断录入（同优先级推荐静默降级策略）。
 */
@Slf4j
@Service
public class AiBugDedupServiceImpl implements AiBugDedupService {

    @Resource
    private AiVectorSearchService vectorSearchService;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiRateLimiter aiRateLimiter;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private SysUserMapper userMapper;

    @Override
    public AiBugDedupRespDTO dedup(UUID userId, UUID workspaceId, UUID projectId, AiBugDedupReqDTO reqDTO) {
        aiRateLimiter.checkAndRecord(userId, AiFunctionType.BUG_DEDUP);
        String semantic = aiConfigService.getStatus().getSemanticSearch();
        if (Constants.AiSemanticSearch.AVAILABLE.equals(semantic)) {
            try {
                int topK = aiConfigService.getIntSetting("dedup.topK");
                double threshold = aiConfigService.getNumberSetting("dedup.similarityThreshold");
                List<BugDedupHit> hits = vectorSearchService.searchSimilarBugs(
                        projectId, reqDTO.getTitle(), reqDTO.getReproSteps(), reqDTO.getExcludeBugId(), topK, threshold);
                return response(false, hits.stream()
                        .map(hit -> new RawHit(hit.bugId(), hit.title(), hit.status(), hit.assigneeId(), hit.similarity()))
                        .toList());
            } catch (Exception e) {
                log.warn("[AI] 语义查重调用失败，降级关键词匹配: {}", e.getMessage());
            }
        }
        return keywordResponse(projectId, reqDTO);
    }

    /** 关键词降级：标题分词后按命中关键词数倒序取 topK（3.2 降级语义，similarity 置 null） */
    private AiBugDedupRespDTO keywordResponse(UUID projectId, AiBugDedupReqDTO reqDTO) {
        int topK = aiConfigService.getIntSetting("dedup.topK");
        List<String> keywords = tokenize(reqDTO.getTitle());
        List<Bug> openBugs = bugMapper.findOpenBugsForDedup(projectId);
        Map<UUID, Integer> matchCount = new HashMap<>();
        for (Bug bug : openBugs) {
            if (reqDTO.getExcludeBugId() != null && reqDTO.getExcludeBugId().equals(bug.getId())) {
                continue;
            }
            int count = (int) keywords.stream()
                    .filter(kw -> bug.getTitle() != null && bug.getTitle().toLowerCase().contains(kw))
                    .count();
            if (count > 0) {
                matchCount.put(bug.getId(), count);
            }
        }
        List<Bug> ranked = openBugs.stream()
                .filter(bug -> matchCount.containsKey(bug.getId()))
                .sorted((a, b) -> matchCount.get(b.getId()).compareTo(matchCount.get(a.getId())))
                .limit(topK)
                .toList();
        return response(true, ranked.stream()
                .map(bug -> new RawHit(bug.getId(), bug.getTitle(), bug.getStatus(), bug.getAssigneeId(), null))
                .toList());
    }

    /** 标题分词：按非字母数字切分，保留 ≥2 字符片段（中英文通用，过滤单字噪声） */
    private List<String> tokenize(String title) {
        if (title == null) {
            return List.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String token : title.toLowerCase().split("[^\\p{L}\\p{Nd}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    private AiBugDedupRespDTO response(boolean degraded, List<RawHit> hits) {
        // 处理人显示名批量解析：distinct assigneeId 一次查全，缺失置空
        Map<UUID, String> names = new HashMap<>();
        List<UUID> assigneeIds = hits.stream().map(RawHit::assigneeId).distinct().toList();
        if (!assigneeIds.isEmpty()) {
            for (SysUser user : userMapper.selectBatchIds(assigneeIds)) {
                names.put(user.getId(), user.getName());
            }
        }
        AiBugDedupRespDTO resp = new AiBugDedupRespDTO();
        resp.setSemanticDegraded(degraded);
        resp.setItems(hits.stream().map(hit -> {
            AiBugDedupRespDTO.Item item = new AiBugDedupRespDTO.Item();
            item.setBugId(hit.bugId());
            item.setTitle(hit.title());
            item.setStatus(hit.status());
            item.setAssigneeName(names.get(hit.assigneeId()));
            item.setSimilarity(hit.similarity());
            return item;
        }).toList());
        return resp;
    }

    /** 查重原始命中（相似度可空：降级模式下为 null），名称由 response 统一解析 */
    private record RawHit(UUID bugId, String title, String status, UUID assigneeId, Double similarity) {
    }
}
