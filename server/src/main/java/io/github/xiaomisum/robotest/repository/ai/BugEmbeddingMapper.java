package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.BugEmbedding;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BugEmbeddingMapper extends BaseMapperX<BugEmbedding> {

    default BugEmbedding findByBugId(UUID bugId) {
        return selectOne(new LambdaQueryWrapperX<BugEmbedding>().eq(BugEmbedding::getBugId, bugId));
    }

    default void logicalDeleteByBugId(UUID bugId) {
        delete(new LambdaQueryWrapperX<BugEmbedding>().eq(BugEmbedding::getBugId, bugId));
    }

    default void logicalDeleteByBugIds(Collection<UUID> bugIds) {
        delete(new LambdaQueryWrapperX<BugEmbedding>().in(BugEmbedding::getBugId, bugIds));
    }

    /**
     * 存量 source_hash 映射（补偿/重建判断过期，仅取三列避免全量 IO）
     */
    default List<BugEmbedding> findHashesByBugIds(Collection<UUID> bugIds) {
        return selectList(new LambdaQueryWrapperX<BugEmbedding>()
                .in(BugEmbedding::getBugId, bugIds)
                .select(BugEmbedding::getBugId, BugEmbedding::getSourceHash));
    }

    default int upsert(BugEmbedding entity) {
        return insertOrUpdateBug(entity.getId().toString(), entity.getBugId().toString(),
                entity.getProjectId().toString(), entity.getEmbedding(), entity.getSourceHash(), entity.getModel());
    }

    /**
     * 1:1 UPSERT（ON CONFLICT 走部分唯一索引 uk_bug_embedding_bug_id，须携带相同 WHERE 谓词）。
     * UUID 以字符串绑定 + SQL 侧 ::uuid 转换，规避注解 SQL 对 UUID 类型处理器的依赖。
     */
    @Insert("INSERT INTO bug_embedding (id, bug_id, project_id, embedding, source_hash, model, is_deleted, created_at, updated_at) "
            + "VALUES (#{id}::uuid, #{bugId}::uuid, #{projectId}::uuid, #{embedding}::vector, #{sourceHash}, #{model}, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (bug_id) WHERE is_deleted = false "
            + "DO UPDATE SET embedding = EXCLUDED.embedding, source_hash = EXCLUDED.source_hash, "
            + "model = EXCLUDED.model, updated_at = CURRENT_TIMESTAMP")
    int insertOrUpdateBug(@Param("id") String id, @Param("bugId") String bugId, @Param("projectId") String projectId,
                          @Param("embedding") String embedding, @Param("sourceHash") String sourceHash,
                          @Param("model") String model);

    /**
     * 语义查重检索（详细设计 4.2 检索 SQL 原样）：HNSW 取 TopK，阈值过滤在应用层。
     * 相似度 = 1 − 余弦距离；closed 缺陷与排除项在 JOIN 后过滤。
     */
    @Select("SELECT b.id::text AS bugId, b.title AS title, b.status AS status, b.assignee_id::text AS assigneeId, "
            + "1 - (e.embedding <=> #{queryVec}::vector) AS similarity "
            + "FROM bug_embedding e JOIN bug b ON b.id = e.bug_id "
            + "WHERE e.project_id = #{projectId}::uuid AND e.is_deleted = FALSE AND b.is_deleted = FALSE "
            + "AND b.status <> 'closed' "
            + "AND (#{excludeBugId}::uuid IS NULL OR b.id <> #{excludeBugId}::uuid) "
            + "ORDER BY e.embedding <=> #{queryVec}::vector "
            + "LIMIT #{topK}")
    List<SearchRow> searchTopK(@Param("projectId") String projectId, @Param("queryVec") String queryVec,
                               @Param("excludeBugId") String excludeBugId, @Param("topK") int topK);

    @Data
    class SearchRow {
        private String bugId;
        private String title;
        private String status;
        private String assigneeId;
        private Double similarity;
    }
}
