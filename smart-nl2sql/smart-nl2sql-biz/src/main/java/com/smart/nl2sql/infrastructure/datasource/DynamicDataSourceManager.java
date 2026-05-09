package com.smart.nl2sql.infrastructure.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.smart.nl2sql.api.dto.DataSourceTestCmd;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic datasource manager: keeps a Druid connection pool per datasource id.
 * Used to obtain JDBC connections for metadata exploration and SQL execution.
 */
@Slf4j
@Component
public class DynamicDataSourceManager {

    private final Map<Long, DruidDataSource> pools = new ConcurrentHashMap<>();

    @Value("${nl2sql.connection-pool.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${nl2sql.connection-pool.min-idle:1}")
    private int minIdle;

    @Value("${nl2sql.query-timeout-seconds:30}")
    private int queryTimeoutSeconds;

    /**
     * Test connection by an ad-hoc command (no caching).
     */
    public boolean testConnection(DataSourceTestCmd cmd) {
        String url = JdbcUrlBuilder.build(cmd.getType(), cmd.getHost(), cmd.getPort(),
                cmd.getDatabaseName(), cmd.getSchemaName());
        String driverClassName = JdbcUrlBuilder.getDriverClassName(cmd.getType());
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            log.error("数据源驱动未找到 type={}, driver={}", cmd.getType(), driverClassName, e);
            return false;
        }
        try (Connection conn = DriverManager.getConnection(url, cmd.getUsername(), cmd.getPassword())) {
            return conn.isValid(queryTimeoutSeconds);
        } catch (SQLException e) {
            log.error("测试数据源连接失败 type={} host={}:{}", cmd.getType(), cmd.getHost(), cmd.getPort(), e);
            return false;
        }
    }

    public boolean testConnectionByEntity(Nl2sqlDatasourceEntity entity) {
        DataSourceTestCmd cmd = new DataSourceTestCmd();
        cmd.setType(entity.getType());
        cmd.setHost(entity.getHost());
        cmd.setPort(entity.getPort());
        cmd.setDatabaseName(entity.getDatabaseName());
        cmd.setSchemaName(entity.getSchemaName());
        cmd.setUsername(entity.getUsername());
        cmd.setPassword(entity.getPassword());
        cmd.setExtraParams(entity.getExtraParams());
        return testConnection(cmd);
    }

    /**
     * Get a connection from the cached pool of the given datasource entity.
     * Caller must close the returned connection (it will be returned to the pool).
     */
    public Connection getConnection(Nl2sqlDatasourceEntity entity) throws SQLException {
        DruidDataSource pool = pools.computeIfAbsent(entity.getId(), id -> buildPool(entity));
        return pool.getConnection();
    }

    /**
     * Evict the cached pool of the given datasource (e.g. after update or delete).
     */
    public void evict(Long datasourceId) {
        DruidDataSource removed = pools.remove(datasourceId);
        if (removed != null) {
            removed.close();
            log.info("已清除并关闭数据源连接池: {}", datasourceId);
        }
    }

    @PreDestroy
    public void destroy() {
        pools.values().forEach(DruidDataSource::close);
        pools.clear();
    }

    private DruidDataSource buildPool(Nl2sqlDatasourceEntity entity) {
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(JdbcUrlBuilder.build(entity));
        ds.setUsername(entity.getUsername());
        ds.setPassword(entity.getPassword());
        ds.setDriverClassName(JdbcUrlBuilder.getDriverClassName(entity.getType()));
        ds.setInitialSize(minIdle);
        ds.setMinIdle(minIdle);
        ds.setMaxActive(maxPoolSize);
        ds.setMaxWait(10_000L);
        ds.setValidationQueryTimeout(queryTimeoutSeconds);
        ds.setTestOnBorrow(false);
        ds.setTestWhileIdle(true);
        ds.setKeepAlive(true);
        log.info("已为数据源创建 Druid 连接池: id={}, type={}, url={}",
                entity.getId(), entity.getType(), ds.getUrl());
        return ds;
    }
}
