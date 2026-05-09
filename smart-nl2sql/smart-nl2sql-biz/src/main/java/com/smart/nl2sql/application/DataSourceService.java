package com.smart.nl2sql.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.nl2sql.api.dto.DataSourceDTO;
import com.smart.nl2sql.api.dto.DataSourceTestCmd;
import com.smart.nl2sql.api.dto.TableMetaVO;
import com.smart.nl2sql.infrastructure.datasource.DynamicDataSourceManager;
import com.smart.nl2sql.infrastructure.datasource.MetadataExplorer;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlDatasourceEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlDatasourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final Nl2sqlDatasourceMapper datasourceMapper;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final MetadataExplorer metadataExplorer;

    public Page<Nl2sqlDatasourceEntity> page(Page<Nl2sqlDatasourceEntity> page, String keyword) {
        LambdaQueryWrapper<Nl2sqlDatasourceEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Nl2sqlDatasourceEntity::getName, keyword);
        }
        wrapper.orderByDesc(Nl2sqlDatasourceEntity::getCreateTime);
        return datasourceMapper.selectPage(page, wrapper);
    }

    public Nl2sqlDatasourceEntity getById(Long id) {
        return datasourceMapper.selectById(id);
    }

    @Transactional
    public void create(DataSourceDTO dto) {
        Nl2sqlDatasourceEntity entity = new Nl2sqlDatasourceEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setStatus(1);
        datasourceMapper.insert(entity);
    }

    @Transactional
    public void update(DataSourceDTO dto) {
        Nl2sqlDatasourceEntity entity = datasourceMapper.selectById(dto.getId());
        if (entity == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        BeanUtils.copyProperties(dto, entity);
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            entity.setPassword(null);
        }
        datasourceMapper.updateById(entity);
        dynamicDataSourceManager.evict(dto.getId());
    }

    @Transactional
    public void delete(Long id) {
        datasourceMapper.deleteById(id);
        dynamicDataSourceManager.evict(id);
    }

    public boolean testConnection(DataSourceTestCmd cmd) {
        return dynamicDataSourceManager.testConnection(cmd);
    }

    public boolean testConnectionById(Long id) {
        Nl2sqlDatasourceEntity entity = datasourceMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        boolean success = dynamicDataSourceManager.testConnectionByEntity(entity);
        entity.setLastTestTime(LocalDateTime.now());
        entity.setLastTestStatus(success ? 1 : 0);
        datasourceMapper.updateById(entity);
        return success;
    }

    public List<TableMetaVO> getTables(Long datasourceId) {
        Nl2sqlDatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        return metadataExplorer.listTables(entity);
    }

    public TableMetaVO getTableColumns(Long datasourceId, String tableName) {
        Nl2sqlDatasourceEntity entity = datasourceMapper.selectById(datasourceId);
        if (entity == null) {
            throw new IllegalArgumentException("数据源不存在");
        }
        return metadataExplorer.getTableDetail(entity, tableName);
    }
}
