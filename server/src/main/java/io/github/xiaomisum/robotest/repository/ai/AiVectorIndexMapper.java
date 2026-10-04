package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiVectorIndex;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

import java.util.List;
import java.util.UUID;

public interface AiVectorIndexMapper extends BaseMapperX<AiVectorIndex> {

    /**
     * 批量写入：向量列须 CAST 成 vector（PG 不做 text → vector 的隐式赋值转换）；
     * id / 时间戳由调用方按框架默认策略（时间有序 UUID、当前时间）填充，本方法不走 MP insertFill。
     */
    @Insert("<script>"
            + "INSERT INTO ai_vector_index "
            + "(id, project_id, entity_type, entity_id, chunk_index, content, embedding, "
            + " embedding_version, indexed_at, created_at, updated_at, is_deleted) VALUES "
            + "<foreach collection=\"rows\" item=\"r\" separator=\",\">"
            + "(#{r.id}, #{r.projectId}, #{r.entityType}, #{r.entityId}, #{r.chunkIndex}, #{r.content}, "
            + " CAST(#{r.embedding} AS vector), #{r.embeddingVersion}, #{r.indexedAt}, "
            + " #{r.createdAt}, #{r.updatedAt}, FALSE)"
            + "</foreach></script>")
    int insertBatch(@Param("rows") List<AiVectorIndex> rows);

    /**
     * 限权相似检索（详设 4.4 读侧）：先按项目 / 实体类型过滤再比对，禁止全库比对；
     * 三种算子统一升序（inner_product 取负内积，值越小越相似，同样升序取最相似）。
     *
     * @param queryVector 查询文本向量的 pgvector 字面量（[0.1,…]）
     */
    @Select("<script>"
            + "SELECT project_id, entity_type, entity_id, chunk_index, content, "
            + " (embedding "
            + "<choose>"
            + "  <when test=\"operator == 'l2'\">&lt;-&gt;</when>"
            + "  <when test=\"operator == 'inner_product'\">&lt;#&gt;</when>"
            + "  <otherwise>&lt;=&gt;</otherwise>"
            + "</choose>"
            + " CAST(#{queryVector} AS vector)) AS distance "
            + "FROM ai_vector_index "
            + "WHERE is_deleted = FALSE "
            + "AND project_id IN <foreach collection=\"projectIds\" item=\"pid\" open=\"(\" close=\")\" separator=\",\">#{pid}</foreach> "
            + "<if test=\"entityType != null and entityType != ''\"> AND entity_type = #{entityType}</if> "
            + "ORDER BY distance ASC "
            + "LIMIT #{limit}"
            + "</script>")
    List<AiVectorSearchHitRespDTO> selectTopK(@Param("queryVector") String queryVector,
            @Param("projectIds") List<UUID> projectIds, @Param("entityType") String entityType,
            @Param("limit") int limit, @Param("operator") String operator);
}
