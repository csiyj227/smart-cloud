package com.smart.codegen.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 代码生成相关工具方法。
 *
 * <p>仅做纯字符串处理与时间格式化，无外部依赖；所有方法 null-safe。
 */
public final class CodeGenUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CodeGenUtils() {
    }

    /**
     * 当前日期字符串，格式 yyyy-MM-dd。
     */
    public static String today() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    /**
     * 把蛇形 / 下划线命名转为大驼峰：sys_user → SysUser。
     */
    public static String toUpperCamel(String snake) {
        if (snake == null || snake.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upperNext = true;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_' || c == '-') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
            upperNext = false;
        }
        return sb.toString();
    }

    /**
     * 把蛇形 / 下划线命名转为小驼峰：sys_user → sysUser。
     */
    public static String toLowerCamel(String snake) {
        String upper = toUpperCamel(snake);
        if (upper.isEmpty()) {
            return upper;
        }
        return Character.toLowerCase(upper.charAt(0)) + upper.substring(1);
    }

    /**
     * 把类名首字母小写：SysUser → sysUser，便于做 bean 名 / 变量名。
     */
    public static String firstLower(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * 包名转目录路径：com.smart.demo → com/smart/demo
     */
    public static String packageToPath(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        return packageName.replace('.', '/');
    }

    /**
     * 去掉表前缀：sys_user → user（用于推导 businessName 默认值）。
     */
    public static String stripTablePrefix(String tableName, String prefix) {
        if (tableName == null) {
            return "";
        }
        if (prefix != null && !prefix.isEmpty() && tableName.startsWith(prefix)) {
            return tableName.substring(prefix.length());
        }
        return tableName;
    }

    /**
     * SQL 列类型 → Java 类型映射。覆盖 PostgreSQL / MySQL 常见类型。
     */
    public static String sqlTypeToJavaType(String sqlType) {
        if (sqlType == null) {
            return "String";
        }
        String lower = sqlType.toLowerCase(Locale.ROOT);
        // 整数族
        if (lower.contains("bigint") || lower.contains("bigserial")) {
            return "Long";
        }
        if (lower.contains("smallint") || lower.contains("tinyint")) {
            return "Integer";
        }
        if (lower.contains("int") || lower.contains("serial")) {
            return "Integer";
        }
        // 浮点 / 货币
        if (lower.contains("decimal") || lower.contains("numeric")
                || lower.contains("money") || lower.contains("float")
                || lower.contains("double") || lower.contains("real")) {
            return "BigDecimal";
        }
        // 布尔
        if (lower.contains("bool") || lower.contains("bit")) {
            return "Boolean";
        }
        // 日期时间
        if (lower.contains("timestamp") || lower.contains("datetime")) {
            return "LocalDateTime";
        }
        if (lower.contains("date")) {
            return "LocalDate";
        }
        if (lower.contains("time")) {
            return "LocalTime";
        }
        // 字符串
        return "String";
    }

    /**
     * 根据 SQL 列类型推导前端 html_type。
     */
    public static String sqlTypeToHtmlType(String sqlType) {
        if (sqlType == null) {
            return "input";
        }
        String lower = sqlType.toLowerCase(Locale.ROOT);
        if (lower.contains("text") || lower.contains("clob")) {
            return "textarea";
        }
        if (lower.contains("timestamp") || lower.contains("datetime")) {
            return "datetime";
        }
        if (lower.contains("date")) {
            return "date";
        }
        if (lower.contains("bool")) {
            return "switch";
        }
        return "input";
    }

    /**
     * 是否为通用字段（自动忽略，不进入插入/编辑/列表）。
     */
    public static boolean isCommonField(String columnName) {
        if (columnName == null) {
            return false;
        }
        return switch (columnName.toLowerCase(Locale.ROOT)) {
            case "id", "create_by", "create_time", "update_by", "update_time",
                 "del_flag", "tenant_id", "version" -> true;
            default -> false;
        };
    }
}
