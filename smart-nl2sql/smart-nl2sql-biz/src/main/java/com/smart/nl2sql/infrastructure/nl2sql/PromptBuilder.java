package com.smart.nl2sql.infrastructure.nl2sql;

import com.smart.nl2sql.api.enums.DataSourceType;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetColumnEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetRelationEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetSampleEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasetTableEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlKnowledgeEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds prompts for NL2SQL / NL2Chart / Data-Insight LLM calls.
 */
@Component
public class PromptBuilder {

    public String buildNl2SqlSystemPrompt(String dataSourceType,
                                          List<Nl2sqlDatasetTableEntity> tables,
                                          List<Nl2sqlDatasetColumnEntity> columns,
                                          List<Nl2sqlDatasetRelationEntity> relations,
                                          List<Nl2sqlKnowledgeEntity> knowledge,
                                          List<Nl2sqlDatasetSampleEntity> samples) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的 SQL 分析师。根据用户的自然语言问题，生成精确的 ")
          .append(dialectName(dataSourceType))
          .append(" SQL 查询语句。\n\n");

        sb.append("## 数据表结构\n");
        Map<String, List<Nl2sqlDatasetColumnEntity>> grouped = columns.stream()
                .collect(Collectors.groupingBy(Nl2sqlDatasetColumnEntity::getTableName));
        for (Nl2sqlDatasetTableEntity table : tables) {
            sb.append("### 表 `").append(table.getTableName()).append("`");
            if (table.getTableAlias() != null && !table.getTableAlias().isBlank()) {
                sb.append("（").append(table.getTableAlias()).append("）");
            }
            sb.append("\n");
            if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
                sb.append("说明：").append(table.getTableComment()).append("\n");
            }
            List<Nl2sqlDatasetColumnEntity> cols = grouped.getOrDefault(table.getTableName(), List.of());
            for (Nl2sqlDatasetColumnEntity col : cols) {
                sb.append("- `").append(col.getColumnName()).append("` ")
                        .append(col.getColumnType());
                String remark = pick(col.getUserRemark(), col.getColumnComment());
                if (remark != null && !remark.isBlank()) {
                    sb.append(" ：").append(remark);
                }
                if (Boolean.TRUE.equals(col.getIsDimension())) {
                    sb.append("【维度】");
                }
                if (Boolean.TRUE.equals(col.getIsMeasure())) {
                    sb.append("【度量】");
                }
                if (Boolean.TRUE.equals(col.getIsPrimaryKey())) {
                    sb.append("【主键】");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (relations != null && !relations.isEmpty()) {
            sb.append("## 表关系\n");
            for (Nl2sqlDatasetRelationEntity rel : relations) {
                sb.append("- ").append(rel.getSourceTable()).append(".").append(rel.getSourceColumn())
                        .append(" ").append(rel.getRelationType() != null ? rel.getRelationType() : "LEFT JOIN")
                        .append(" ").append(rel.getTargetTable()).append(".").append(rel.getTargetColumn())
                        .append("\n");
            }
            sb.append("\n");
        }

        if (knowledge != null && !knowledge.isEmpty()) {
            sb.append("## 业务知识\n");
            for (Nl2sqlKnowledgeEntity k : knowledge) {
                sb.append("- [").append(k.getType()).append("] ");
                if (k.getTitle() != null) {
                    sb.append(k.getTitle()).append(": ");
                }
                sb.append(k.getContent()).append("\n");
            }
            sb.append("\n");
        }

        if (samples != null && !samples.isEmpty()) {
            sb.append("## 参考示例\n");
            for (Nl2sqlDatasetSampleEntity s : samples) {
                sb.append("- 问：").append(s.getQuestion()).append("\n");
                sb.append("  SQL：").append(s.getSqlText()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("## 输出要求\n")
          .append("1. 只输出一条可直接执行的 SELECT 语句，不要任何 markdown 代码块或额外说明\n")
          .append("2. 必须使用提供的表和字段，禁止臆造\n")
          .append("3. 合理使用 GROUP BY、ORDER BY、LIMIT\n")
          .append("4. 字段别名使用中文（与用户问题对应）\n")
          .append("5. 日期函数使用 ").append(dialectName(dataSourceType)).append(" 语法\n");
        return sb.toString();
    }

    public String buildNl2ChartPrompt(String userQuestion,
                                      SqlAnalyzer.AnalysisResult analysis,
                                      List<SqlExecutor.ColumnInfo> columns,
                                      List<Map<String, Object>> sampleRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据用户问题和 SQL 查询结果，推荐最合适的可视化方案。\n\n");
        sb.append("## 用户问题\n").append(userQuestion).append("\n\n");

        sb.append("## 维度字段\n");
        analysis.getDimensions().forEach(d ->
                sb.append("- ").append(d.getAlias()).append(" (").append(d.getExpression()).append(")\n"));

        sb.append("\n## 度量字段\n");
        analysis.getMeasures().forEach(m ->
                sb.append("- ").append(m.getAlias()).append(" (").append(m.getExpression()).append(")\n"));

        sb.append("\n## 结果列\n");
        columns.forEach(c -> sb.append("- ").append(c.getName()).append(" : ").append(c.getTypeName()).append("\n"));

        sb.append("\n## 数据采样（前 5 行）\n");
        int limit = Math.min(5, sampleRows.size());
        for (int i = 0; i < limit; i++) {
            sb.append(sampleRows.get(i)).append("\n");
        }

        sb.append("\n## 输出 JSON\n")
          .append("{\n")
          .append("  \"chartType\": \"bar|line|pie|table\",\n")
          .append("  \"reason\": \"选择该图表类型的原因\",\n")
          .append("  \"config\": {\n")
          .append("    \"title\": \"图表标题\",\n")
          .append("    \"xAxisField\": \"维度字段名\",\n")
          .append("    \"yAxisFields\": [\"度量字段名\"]\n")
          .append("  }\n")
          .append("}\n\n")
          .append("## 选择规则\n")
          .append("- 时间趋势 → line\n")
          .append("- 分类对比 → bar\n")
          .append("- 占比分布（维度 ≤ 8）→ pie\n")
          .append("- 多维度/明细数据 → table\n")
          .append("\n只输出 JSON，不要任何额外解释。");
        return sb.toString();
    }

    public String buildDataInsightPrompt(String userQuestion, String sql,
                                         List<SqlExecutor.ColumnInfo> columns,
                                         List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于查询结果给出业务洞察，简洁明了，不超过 200 字。\n\n");
        sb.append("## 用户问题\n").append(userQuestion).append("\n\n");
        sb.append("## SQL\n").append(sql).append("\n\n");
        sb.append("## 数据（前 10 行，共 ").append(rows.size()).append(" 行）\n");
        sb.append("列：")
          .append(columns.stream().map(SqlExecutor.ColumnInfo::getName).collect(Collectors.joining(", ")))
          .append("\n");
        int limit = Math.min(10, rows.size());
        for (int i = 0; i < limit; i++) {
            sb.append(rows.get(i)).append("\n");
        }
        sb.append("\n请直接给出洞察文字，不要 markdown 标题。");
        return sb.toString();
    }

    private String dialectName(String type) {
        try {
            return DataSourceType.fromCode(type).getLabel();
        } catch (Exception e) {
            return "SQL";
        }
    }

    private String pick(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
