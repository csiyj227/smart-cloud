package com.smart.nl2sql.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DataSourceType {
    MYSQL("mysql", "MySQL", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
    POSTGRESQL("postgresql", "PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s?currentSchema=%s"),
    ORACLE("oracle", "Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@%s:%d:%s"),
    GAUSSDB("gaussdb", "GaussDB", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s?currentSchema=%s");

    private final String code;
    private final String label;
    private final String driverClassName;
    private final String jdbcUrlTemplate;

    public static DataSourceType fromCode(String code) {
        for (DataSourceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported datasource type: " + code);
    }
}