package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.repository.ai.AiVectorIndexMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiEmbeddingClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 向量检索（详设 4.4 读侧）：限权先行 —— 项目集为空直接不返回（无权限即无结果），
 * 再按项目 / 实体类型过滤做相似检索；未配置与重建中的不可用原因分别抛 1000018119 / 1000018122，
 * 由各 AI 能力入口向用户说明，不降级为无引用生成（4.4 / 4.5）。
 */
@Component
public class VectorSearchService {

    @Resource
    private AiEmbeddingGate gate;
    @Resource
    private AiEmbeddingClient embeddingClient;
    @Resource
    private AiVectorIndexMapper indexMapper;

    /**
     * @param query       检索文本
     * @param projectIds  调用方已解析的授权项目集（项目内任务 = 单项目，助手 = 活跃空间项目集）
     * @param entityType  实体类型过滤，null / 空 = 不限类型
     * @param topK        取回条数，由调用方按能力语义给出（≤ 0 视为空结果，不发明默认口径）
     * @param userId      查询向量化的用量记账人
     */
    public List<AiVectorSearchHitRespDTO> search(String query, List<UUID> projectIds, String entityType,
            int topK, UUID userId) {
        if (!StringUtils.hasText(query) || projectIds == null || projectIds.isEmpty() || topK <= 0) {
            return List.of();
        }
        AiEmbeddingConfig config = gate.requireReady();
        AiEmbeddingClient.EmbeddingReply reply = embeddingClient.embed(
                config, List.of(query), userId, null, null);
        return indexMapper.selectTopK(VectorIndexService.toVectorText(reply.embedding()),
                projectIds, entityType, topK, config.getOperator());
    }
}
