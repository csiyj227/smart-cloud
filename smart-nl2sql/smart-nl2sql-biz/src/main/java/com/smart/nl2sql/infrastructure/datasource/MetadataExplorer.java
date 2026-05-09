package com.smart.nl2sql.infrastructure.datasource;

import com.smart.nl2sql.api.dto.ColumnMetaVO;
import com.smart.nl2sql.api.dto.TableMetaVO;
import com.smart.nl2sql.api.enums.DataSourceType;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Database metadata explorer that supports MySQL / PostgreSQL / Oracle / GaussDB.
 * Uses {@link DynamicDataSourceManager} to obtain (cached) connections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataExplorer {

    private final DynamicDataSourceManager dataSourceManager;

    public List<TableMetaVO> listTables(Nl2sqlDatasourceEntity datasource) {
        List<TableMetaVO> tables = new ArrayList<>();
        try (Connection conn = dataSourceManager.getConnection(datasource)) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = JdbcUrlBuilder.getMetadataSchema(datasource);
            try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    TableMetaVO table = new TableMetaVO();
                    table.setTableName(rs.getString("TABLE_NAME"));
                    table.setTableComment(rs.getString("REMARKS"));
                    table.setTableType(rs.getString("TABLE_TYPE"));
                    tables.add(table);
                }
            }
        } catch (Exception e) {
            log.error("获取表列表失败 datasourceId={}", datasource.getId(), e);
            throw new IllegalStateException("获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    public TableMetaVO getTableDetail(Nl2sqlDatasourceEntity datasource, String tableName) {
        TableMetaVO tableMeta = new TableMetaVO();
        tableMeta.setTableName(tableName);
        List<ColumnMetaVO> columns = new ArrayList<>();
        try (Connection conn = dataSourceManager.getConnection(datasource)) {
            DatabaseMetaData metaData = conn.getMetaData();
            DataSourceType dsType = DataSourceType.fromCode(datasource.getType());

            // PostgreSQL/GaussDB: 用 schema 过滤，catalog 必须为 null（否则 PG 驱动会忽略 schema 参数导致跨 schema 匹配错乱）。
            // MySQL: catalog == database，schema = null。
            // Oracle: schema = username（大写），catalog = null。
            String catalog;
            String schema = JdbcUrlBuilder.getMetadataSchema(datasource);
            switch (dsType) {
                case POSTGRESQL, GAUSSDB, ORACLE -> catalog = null;
                default -> catalog = conn.getCatalog();
            }

            // 表名标准化：PG/GaussDB 默认全部小写存储；如果用户传入 "public.users" 这种带 schema 前缀的形式，先剥离。
            // Oracle 默认全部大写存储。
            String[] schemaTable = splitSchemaTable(tableName);
            if (schemaTable[0] != null) {
                schema = schemaTable[0];
            }
            String resolvedTable = normalizeTableName(schemaTable[1], dsType);

            log.debug("getTableDetail datasourceId={} input='{}' → catalog={} schema={} table={}",
                    datasource.getId(), tableName, catalog, schema, resolvedTable);

            try (ResultSet rs = metaData.getColumns(catalog, schema, resolvedTable, "%")) {
                while (rs.next()) {
                    ColumnMetaVO column = new ColumnMetaVO();
                    column.setColumnName(rs.getString("COLUMN_NAME"));
                    column.setColumnType(rs.getString("TYPE_NAME"));
                    column.setColumnComment(rs.getString("REMARKS"));
                    column.setIsNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                    column.setDefaultValue(rs.getString("COLUMN_DEF"));
                    column.setIsPrimaryKey(false);
                    columns.add(column);
                }
            }

            // 兜底：PG/GaussDB 大小写保护——如果传入的就是小写但 metadata 用了不同存储方式，再尝试一次相反的大小写
            if (columns.isEmpty() && (dsType == DataSourceType.POSTGRESQL || dsType == DataSourceType.GAUSSDB)) {
                String alt = resolvedTable.equals(resolvedTable.toLowerCase())
                        ? resolvedTable.toUpperCase() : resolvedTable.toLowerCase();
                log.warn("PG/GaussDB getColumns 首次查询无结果，回退尝试 table='{}'", alt);
                try (ResultSet rs = metaData.getColumns(catalog, schema, alt, "%")) {
                    while (rs.next()) {
                        ColumnMetaVO column = new ColumnMetaVO();
                        column.setColumnName(rs.getString("COLUMN_NAME"));
                        column.setColumnType(rs.getString("TYPE_NAME"));
                        column.setColumnComment(rs.getString("REMARKS"));
                        column.setIsNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                        column.setDefaultValue(rs.getString("COLUMN_DEF"));
                        column.setIsPrimaryKey(false);
                        columns.add(column);
                    }
                }
                if (!columns.isEmpty()) {
                    resolvedTable = alt;
                }
            }

            try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, resolvedTable)) {
                while (pkRs.next()) {
                    String pkColumn = pkRs.getString("COLUMN_NAME");
                    columns.stream()
                            .filter(col -> col.getColumnName().equalsIgnoreCase(pkColumn))
                            .findFirst()
                            .ifPresent(col -> col.setIsPrimaryKey(true));
                }
            }

            tableMeta.setColumns(columns);
            if (columns.isEmpty()) {
                log.warn("获取字段为空 datasourceId={} table='{}' (resolved={}) schema={} —— 请确认表存在于该 schema",
                        datasource.getId(), tableName, resolvedTable, schema);
            }
        } catch (Exception e) {
            log.error("获取表详情失败 datasourceId={} table={}", datasource.getId(), tableName, e);
            throw new IllegalStateException("获取表详情失败: " + e.getMessage(), e);
        }
        return tableMeta;
    }

    /** 把 "schema.table" 拆成 [schema, table]；无 schema 前缀时 schema 为 null。 */
    private String[] splitSchemaTable(String raw) {
        if (raw == null) {
            return new String[]{null, null};
        }
        int dot = raw.indexOf('.');
        if (dot > 0 && dot < raw.length() - 1) {
            return new String[]{raw.substring(0, dot), raw.substring(dot + 1)};
        }
        return new String[]{null, raw};
    }

    /**
     * 按数据库默认大小写策略归一表名。
     * 注意：仅当表名是「未加引号的标识符」时才归一；如果包含引号说明用户希望保留原样。
     */
    private String normalizeTableName(String table, DataSourceType dsType) {
        if (table == null || table.contains("\"") || table.contains("`")) {
            return table;
        }
        return switch (dsType) {
            case POSTGRESQL, GAUSSDB -> table.toLowerCase();
            case ORACLE -> table.toUpperCase();
            default -> table;
        };
    }
}
