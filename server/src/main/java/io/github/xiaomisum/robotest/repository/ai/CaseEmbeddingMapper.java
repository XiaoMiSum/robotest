package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.CaseEmbedding;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CaseEmbeddingMapper extends BaseMapperX<CaseEmbedding> {

    default CaseEmbedding findByNodeId(UUID nodeId) {
        return selectOne(new LambdaQueryWrapperX<CaseEmbedding>().eq(CaseEmbedding::getNodeId, nodeId));
    }

    default void logicalDeleteByNodeId(UUID nodeId) {
        delete(new LambdaQueryWrapperX<CaseEmbedding>().eq(CaseEmbedding::getNodeId, nodeId));
    }

    default void logicalDeleteByNodeIds(Collection<UUID> nodeIds) {
        delete(new LambdaQueryWrapperX<CaseEmbedding>().in(CaseEmbedding::getNodeId, nodeIds));
    }

    /**
     * 存量 source_hash 映射（补偿/重建判断过期，仅取三列避免全量 IO）
     */
    default List<CaseEmbedding> findHashesByNodeIds(Collection<UUID> nodeIds) {
        return selectList(new LambdaQueryWrapperX<CaseEmbedding>()
                .in(CaseEmbedding::getNodeId, nodeIds)
                .select(CaseEmbedding::getNodeId, CaseEmbedding::getSourceHash));
    }

    default int upsert(CaseEmbedding entity) {
        return insertOrUpdateCase(entity.getId().toString(), entity.getNodeId().toString(),
                entity.getProjectId().toString(), entity.getEmbedding(), entity.getSourceHash(), entity.getModel());
    }

    /**
     * 1:1 UPSERT（ON CONFLICT 走部分唯一索引 uk_case_embedding_node_id，须携带相同 WHERE 谓词）
     */
    @Insert("INSERT INTO case_embedding (id, node_id, project_id, embedding, source_hash, model, is_deleted, created_at, updated_at) "
            + "VALUES (#{id}::uuid, #{nodeId}::uuid, #{projectId}::uuid, #{embedding}::vector, #{sourceHash}, #{model}, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (node_id) WHERE is_deleted = false "
            + "DO UPDATE SET embedding = EXCLUDED.embedding, source_hash = EXCLUDED.source_hash, "
            + "model = EXCLUDED.model, updated_at = CURRENT_TIMESTAMP")
    int insertOrUpdateCase(@Param("id") String id, @Param("nodeId") String nodeId, @Param("projectId") String projectId,
                           @Param("embedding") String embedding, @Param("sourceHash") String sourceHash,
                           @Param("model") String model);

    /**
     * 用例向量近邻检索（同项目范围内，供 §4.1.1 用例查重），topK 取回后应用层阈值过滤
     */
    @Select("SELECT c.node_id::text AS nodeId, 1 - (c.embedding <=> #{queryVec}::vector) AS similarity "
            + "FROM case_embedding c "
            + "WHERE c.project_id = #{projectId}::uuid AND c.is_deleted = FALSE "
            + "ORDER BY c.embedding <=> #{queryVec}::vector "
            + "LIMIT #{topK}")
    List<SearchRow> searchTopK(@Param("projectId") String projectId, @Param("queryVec") String queryVec,
                               @Param("topK") int topK);

    @Data
    class SearchRow {
        private String nodeId;
        private Double similarity;
    }
}
