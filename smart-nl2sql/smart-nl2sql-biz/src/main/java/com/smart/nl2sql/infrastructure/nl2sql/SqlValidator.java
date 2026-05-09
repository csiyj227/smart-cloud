package com.smart.nl2sql.infrastructure.nl2sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * SQL safety validator. Only SELECT statements are allowed; all DML/DDL keywords are rejected.
 */
@Slf4j
@Component
public class SqlValidator {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|truncate|alter|create|grant|revoke|merge|call|exec|execute)\\b");

    private static final Pattern MULTI_STATEMENT = Pattern.compile(";\\s*\\S+");

    /**
     * Validate the SQL is a single safe SELECT statement.
     *
     * @throws IllegalArgumentException if invalid
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String trimmed = sql.trim();
        // Strip trailing semicolons for the multi-statement check
        String withoutTrailingSemi = trimmed.replaceAll(";+\\s*$", "");
        if (MULTI_STATEMENT.matcher(withoutTrailingSemi).find()) {
            throw new IllegalArgumentException("不允许执行多条 SQL 语句");
        }

        String stripped = stripComments(withoutTrailingSemi);
        String upper = stripped.trim().toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("仅允许执行 SELECT 查询语句");
        }
        if (FORBIDDEN.matcher(stripped).find()) {
            throw new IllegalArgumentException("SQL 中包含禁止的关键字（如 INSERT/UPDATE/DELETE/DROP 等）");
        }
    }

    /**
     * Append a LIMIT clause if not present, to avoid full-table scans.
     */
    public String enforceLimit(String sql, int maxRows) {
        String trimmed = sql.trim().replaceAll(";+\\s*$", "");
        String upper = trimmed.toUpperCase();
        if (upper.contains(" LIMIT ") || upper.matches("(?s).*\\bFETCH\\s+(FIRST|NEXT)\\b.*")) {
            return trimmed;
        }
        return trimmed + " LIMIT " + maxRows;
    }

    private String stripComments(String sql) {
        // Remove /* ... */ and -- line comments
        return sql.replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("--.*?(\\r?\\n|$)", " ");
    }
}
