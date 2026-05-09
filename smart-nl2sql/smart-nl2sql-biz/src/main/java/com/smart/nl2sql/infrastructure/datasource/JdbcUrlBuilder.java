package com.smart.nl2sql.infrastructure.datasource;

import com.smart.nl2sql.api.enums.DataSourceType;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;

/**
 * Builds JDBC URL based on datasource type.
 * Supports MySQL / PostgreSQL / Oracle / GaussDB.
 */
public final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
    }

    public static String build(String type, String host, Integer port, String databaseName, String schemaName) {
        DataSourceType dsType = DataSourceType.fromCode(type);
        return switch (dsType) {
            case MYSQL -> String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                    host, port, safe(databaseName));
            case POSTGRESQL -> String.format(
                    "jdbc:postgresql://%s:%d/%s%s",
                    host, port, safe(databaseName),
                    schemaName != null && !schemaName.isBlank() ? "?currentSchema=" + schemaName : "");
            case ORACLE -> String.format(
                    "jdbc:oracle:thin:@%s:%d:%s",
                    host, port, safe(databaseName));
            case GAUSSDB -> String.format(
                    "jdbc:postgresql://%s:%d/%s%s",
                    host, port, safe(databaseName),
                    schemaName != null && !schemaName.isBlank() ? "?currentSchema=" + schemaName : "");
        };
    }

    public static String build(Nl2sqlDatasourceEntity entity) {
        return build(entity.getType(), entity.getHost(), entity.getPort(),
                entity.getDatabaseName(), entity.getSchemaName());
    }

    public static String getDriverClassName(String type) {
        return DataSourceType.fromCode(type).getDriverClassName();
    }

    /**
     * Get default schema for metadata exploration. PostgreSQL/GaussDB use schemaName,
     * MySQL/Oracle use null (will use catalog instead).
     */
    public static String getMetadataSchema(Nl2sqlDatasourceEntity entity) {
        DataSourceType dsType = DataSourceType.fromCode(entity.getType());
        return switch (dsType) {
            case POSTGRESQL, GAUSSDB -> entity.getSchemaName() != null && !entity.getSchemaName().isBlank()
                    ? entity.getSchemaName() : "public";
            case ORACLE -> entity.getUsername() != null ? entity.getUsername().toUpperCase() : null;
            default -> null;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
