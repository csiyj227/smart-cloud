package com.smart.nl2sql.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetSegmentEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 数据集向量段 Mapper。
 *
 * <p>核心点：embedding 字段是 {@code vector(1024)}，MyBatis-Plus 的默认 BaseMapper.insert
 * 没法把 Java String 自动绑定为 pgvector 类型，所以这里提供两个原生 SQL 方法：
 * <ul>
 *     <li>{@link #insertWithVector}：写入时用 {@code ?::vector} 强转字面量</li>
 *     <li>{@link #searchByVector}：按余弦距离检索，返回 segment + distance</li>
 * </ul>
 */
@Mapper
public interface Nl2sqlDatasetSegmentMapper extends BaseMapper<Nl2sqlDatasetSegmentEntity> {

    /**
     * 插入一条带向量的 segment。
     * embedding 入参形如 "[0.12,0.34,...]" 字面量字符串，由 PG 端转换为 vector 类型。
     */
    @Insert("INSERT INTO nl2sql_dataset_segment "
            + "(dataset_id, segment_type, ref_id, ref_table, ref_label, content, token_count, embedding) "
            + "VALUES (#{datasetId}, #{segmentType}, #{refId}, #{refTable}, #{refLabel}, "
            + "#{content}, #{tokenCount}, #{embedding}::vector)")
    int insertWithVector(Nl2sqlDatasetSegmentEntity entity);

    /**
     * 删除指定数据集的全部段（学习时全量重建用）。
     * 用 hard delete 而非逻辑删除：向量段是派生数据，没有保留旧版本的价值。
     */
    @Delete("DELETE FROM nl2sql_dataset_segment WHERE dataset_id = #{datasetId}")
    int deleteByDatasetId(@Param("datasetId") Long datasetId);

    /**
     * 余弦距离 top-K 检索。返回每行包含 segment 全部字段 + distance（升序，越小越相似）。
     *
     * @param datasetId  数据集 id（必填，做强过滤）
     * @param queryVec   查询向量字面量字符串
     * @param topK       返回条数上限
     * @param maxDistance 余弦距离上限；为 null 时不过滤（返回最相似的 topK 条）
     * @return List of Map：包含 id/segment_type/ref_table/ref_label/content/distance
     */
    @Select("<script>"
            + "SELECT id, dataset_id, segment_type, ref_id, ref_table, ref_label, "
            + "content, token_count, "
            + "(embedding &lt;=&gt; #{queryVec}::vector) AS distance "
            + "FROM nl2sql_dataset_segment "
            + "WHERE dataset_id = #{datasetId} "
            + "AND embedding IS NOT NULL "
            + "<if test='maxDistance != null'>"
            + "  AND (embedding &lt;=&gt; #{queryVec}::vector) &lt; #{maxDistance} "
            + "</if>"
            + "ORDER BY embedding &lt;=&gt; #{queryVec}::vector "
            + "LIMIT #{topK}"
            + "</script>")
    List<Map<String, Object>> searchByVector(@Param("datasetId") Long datasetId,
                                             @Param("queryVec") String queryVec,
                                             @Param("topK") int topK,
                                             @Param("maxDistance") Double maxDistance);
}
