package com.smart.ai.infrastructure.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Direct JDBC helper for pgvector operations on {@code ai_knowledge_segment}.
 *
 * <p>Why a JDBC helper instead of MyBatis-Plus?
 * <ul>
 *   <li>pgvector's {@code vector(N)} type can't round-trip cleanly through MyBatis BaseMapper
 *       without a custom TypeHandler. Going through JdbcTemplate with literal cast is simpler
 *       and lets us use pgvector's native operators ({@code <=>}, {@code <->}, {@code <#>}).</li>
 *   <li>HNSW similarity search needs raw SQL with {@code ORDER BY embedding <=> '[...]'::vector}
 *       which has no clean MP equivalent.</li>
 * </ul>
 *
 * <p>Vector serialization format follows pgvector text input:
 * {@code [0.1,0.2,0.3]} — a JSON-like array surrounded by square brackets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreJdbcHelper {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Insert a knowledge segment with its embedding vector.
     *
     * @param kbId          knowledge base id
     * @param documentId    document id
     * @param segmentIndex  ordinal index within the document
     * @param content       chunk content
     * @param tokenCount    rough token count
     * @param embedding     embedding vector (length must match the column dim, default 1536)
     * @param tenantId      tenant id
     * @return generated segment id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long insertSegmentWithEmbedding(Long kbId,
                                           Long documentId,
                                           Integer segmentIndex,
                                           String content,
                                           Integer tokenCount,
                                           float[] embedding,
                                           Long tenantId) {
        String sql = "INSERT INTO ai_knowledge_segment "
                + "(kb_id, document_id, segment_index, content, token_count, embedding, status, tenant_id, create_time, update_time, del_flag) "
                + "VALUES (?, ?, ?, ?, ?, ?::vector, '1', ?, NOW(), NOW(), '0') RETURNING id";

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                kbId, documentId, segmentIndex, content, tokenCount,
                toVectorLiteral(embedding),
                tenantId
        );
    }

    /**
     * Insert a knowledge segment without embedding (fallback when no embedding model configured).
     */
    @Transactional(rollbackFor = Exception.class)
    public Long insertSegmentWithoutEmbedding(Long kbId,
                                              Long documentId,
                                              Integer segmentIndex,
                                              String content,
                                              Integer tokenCount,
                                              Long tenantId) {
        String sql = "INSERT INTO ai_knowledge_segment "
                + "(kb_id, document_id, segment_index, content, token_count, status, tenant_id, create_time, update_time, del_flag) "
                + "VALUES (?, ?, ?, ?, ?, '1', ?, NOW(), NOW(), '0') RETURNING id";

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                kbId, documentId, segmentIndex, content, tokenCount, tenantId
        );
    }

    /**
     * Vector similarity search using cosine distance (HNSW index).
     *
     * <p>Returns the top-K segments most similar to the query embedding, ordered by
     * cosine distance ascending (smaller = more similar).
     *
     * @param kbId             knowledge base id (filter)
     * @param queryEmbedding   the query vector
     * @param topK             max results to return
     * @param maxDistance      threshold; only return results whose distance ≤ this
     *                         (cosine distance ∈ [0, 2]; ~0.3 means very similar). Pass null to disable.
     * @return list of {@code {id, document_id, content, distance}} maps, ordered by similarity
     */
    public List<VectorSearchHit> searchByVector(Long kbId,
                                                float[] queryEmbedding,
                                                int topK,
                                                Double maxDistance) {
        String literal = toVectorLiteral(queryEmbedding);
        StringBuilder sql = new StringBuilder()
                .append("SELECT id, document_id, content, ")
                .append("(embedding <=> ?::vector) AS distance ")
                .append("FROM ai_knowledge_segment ")
                .append("WHERE kb_id = ? AND status = '1' AND del_flag = '0' AND embedding IS NOT NULL ");
        List<Object> params = new ArrayList<>();
        params.add(literal);
        params.add(kbId);
        if (maxDistance != null) {
            sql.append("AND (embedding <=> ?::vector) <= ? ");
            params.add(literal);
            params.add(maxDistance);
        }
        sql.append("ORDER BY embedding <=> ?::vector ASC LIMIT ?");
        params.add(literal);
        params.add(topK);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<VectorSearchHit> hits = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            VectorSearchHit hit = new VectorSearchHit();
            hit.id = ((Number) row.get("id")).longValue();
            hit.documentId = ((Number) row.get("document_id")).longValue();
            hit.content = (String) row.get("content");
            hit.distance = ((Number) row.get("distance")).doubleValue();
            hits.add(hit);
        }
        return hits;
    }

    /**
     * Convert {@code float[]} → pgvector text literal {@code "[0.1,0.2,...]"}.
     */
    public static String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 12);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /** Lightweight DTO for a vector search hit. */
    public static class VectorSearchHit {
        public Long id;
        public Long documentId;
        public String content;
        /** Cosine distance, in [0, 2]. Lower = more similar. Similarity = 1 - distance. */
        public double distance;
    }
}
