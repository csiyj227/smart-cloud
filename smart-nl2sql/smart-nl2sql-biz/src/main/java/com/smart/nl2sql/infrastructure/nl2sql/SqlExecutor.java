package com.smart.nl2sql.infrastructure.nl2sql;

import com.smart.nl2sql.infrastructure.datasource.DynamicDataSourceManager;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes safe SELECT SQL against a user-defined datasource and returns rows with column metadata.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecutor {

    private final DynamicDataSourceManager dataSourceManager;
    private final SqlValidator sqlValidator;

    @Value("${nl2sql.max-query-rows:1000}")
    private int maxRows;

    @Value("${nl2sql.query-timeout-seconds:30}")
    private int queryTimeoutSeconds;

    public ExecutionResult execute(Nl2sqlDatasourceEntity datasource, String sql) {
        sqlValidator.validate(sql);
        String safeSql = sqlValidator.enforceLimit(sql, maxRows);

        long start = System.currentTimeMillis();
        List<ColumnInfo> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = dataSourceManager.getConnection(datasource);
             PreparedStatement stmt = conn.prepareStatement(safeSql)) {
            stmt.setQueryTimeout(queryTimeoutSeconds);
            stmt.setFetchSize(Math.min(maxRows, 500));
            stmt.setMaxRows(maxRows);

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(new ColumnInfo(
                            meta.getColumnLabel(i),
                            meta.getColumnTypeName(i),
                            meta.getColumnClassName(i)));
                }
                while (rs.next() && rows.size() < maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            long cost = System.currentTimeMillis() - start;
            log.error("执行 SQL 失败 datasourceId={} cost={}ms sql={}", datasource.getId(), cost, safeSql, e);
            return ExecutionResult.failed(safeSql, cost, e.getMessage());
        }
        long cost = System.currentTimeMillis() - start;
        log.info("执行 SQL 成功 datasourceId={} rows={} cost={}ms", datasource.getId(), rows.size(), cost);
        return ExecutionResult.success(safeSql, columns, rows, cost);
    }

    @Data
    @AllArgsConstructor
    public static class ColumnInfo {
        private String name;
        private String typeName;
        private String javaClassName;
    }

    @Data
    public static class ExecutionResult {
        private boolean success;
        private String sql;
        private List<ColumnInfo> columns;
        private List<Map<String, Object>> rows;
        private long executionMillis;
        private String errorMessage;

        public static ExecutionResult success(String sql, List<ColumnInfo> cols,
                                              List<Map<String, Object>> rows, long cost) {
            ExecutionResult r = new ExecutionResult();
            r.success = true;
            r.sql = sql;
            r.columns = cols;
            r.rows = rows;
            r.executionMillis = cost;
            return r;
        }

        public static ExecutionResult failed(String sql, long cost, String error) {
            ExecutionResult r = new ExecutionResult();
            r.success = false;
            r.sql = sql;
            r.executionMillis = cost;
            r.errorMessage = error;
            r.columns = new ArrayList<>();
            r.rows = new ArrayList<>();
            return r;
        }
    }
}
