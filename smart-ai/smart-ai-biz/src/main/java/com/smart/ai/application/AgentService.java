package com.smart.ai.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.AgentCmd;
import com.smart.ai.infrastructure.persistence.entity.AiAgentEntity;
import com.smart.ai.infrastructure.persistence.entity.AiAgentKnowledgeEntity;
import com.smart.ai.infrastructure.persistence.entity.AiAgentToolEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiAgentKnowledgeMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiAgentMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiAgentToolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent management service.
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AiAgentMapper agentMapper;
    private final AiAgentToolMapper agentToolMapper;
    private final AiAgentKnowledgeMapper agentKnowledgeMapper;

    public IPage<AiAgentEntity> page(Page<AiAgentEntity> page, String keyword, String category) {
        return agentMapper.selectPage(page, Wrappers.<AiAgentEntity>lambdaQuery()
                .like(keyword != null, AiAgentEntity::getAgentName, keyword)
                .eq(category != null, AiAgentEntity::getCategory, category)
                .eq(AiAgentEntity::getStatus, "1")
                .orderByAsc(AiAgentEntity::getSortOrder));
    }

    public List<AiAgentEntity> listPublic() {
        return agentMapper.selectList(Wrappers.<AiAgentEntity>lambdaQuery()
                .eq(AiAgentEntity::getIsPublic, true)
                .eq(AiAgentEntity::getStatus, "1")
                .orderByAsc(AiAgentEntity::getSortOrder));
    }

    public AiAgentEntity getById(Long id) {
        return agentMapper.selectById(id);
    }

    public List<Long> getToolIds(Long agentId) {
        return agentToolMapper.selectList(Wrappers.<AiAgentToolEntity>lambdaQuery()
                .eq(AiAgentToolEntity::getAgentId, agentId)
                .orderByAsc(AiAgentToolEntity::getSortOrder))
                .stream().map(AiAgentToolEntity::getMcpToolId).toList();
    }

    public List<Long> getKnowledgeBaseIds(Long agentId) {
        return agentKnowledgeMapper.selectList(Wrappers.<AiAgentKnowledgeEntity>lambdaQuery()
                .eq(AiAgentKnowledgeEntity::getAgentId, agentId)
                .orderByAsc(AiAgentKnowledgeEntity::getSortOrder))
                .stream().map(AiAgentKnowledgeEntity::getKnowledgeBaseId).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(AgentCmd cmd) {
        AiAgentEntity entity = new AiAgentEntity();
        BeanUtils.copyProperties(cmd, entity);
        agentMapper.insert(entity);
        saveBindings(entity.getId(), cmd.getToolIds(), cmd.getKnowledgeBaseIds());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(AgentCmd cmd) {
        AiAgentEntity entity = new AiAgentEntity();
        BeanUtils.copyProperties(cmd, entity);
        agentMapper.updateById(entity);
        // Rebind tools and knowledge bases
        agentToolMapper.delete(Wrappers.<AiAgentToolEntity>lambdaQuery()
                .eq(AiAgentToolEntity::getAgentId, cmd.getId()));
        agentKnowledgeMapper.delete(Wrappers.<AiAgentKnowledgeEntity>lambdaQuery()
                .eq(AiAgentKnowledgeEntity::getAgentId, cmd.getId()));
        saveBindings(cmd.getId(), cmd.getToolIds(), cmd.getKnowledgeBaseIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        agentToolMapper.delete(Wrappers.<AiAgentToolEntity>lambdaQuery()
                .eq(AiAgentToolEntity::getAgentId, id));
        agentKnowledgeMapper.delete(Wrappers.<AiAgentKnowledgeEntity>lambdaQuery()
                .eq(AiAgentKnowledgeEntity::getAgentId, id));
        agentMapper.deleteById(id);
    }

    /**
     * Rebind MCP tools for an agent (replaces all existing bindings).
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebindTools(Long agentId, List<Long> toolIds) {
        agentToolMapper.delete(Wrappers.<AiAgentToolEntity>lambdaQuery()
                .eq(AiAgentToolEntity::getAgentId, agentId));
        if (toolIds != null) {
            for (int i = 0; i < toolIds.size(); i++) {
                AiAgentToolEntity tool = new AiAgentToolEntity();
                tool.setAgentId(agentId);
                tool.setMcpToolId(toolIds.get(i));
                tool.setSortOrder(i);
                agentToolMapper.insert(tool);
            }
        }
    }

    /**
     * Rebind knowledge bases for an agent (replaces all existing bindings).
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebindKnowledgeBases(Long agentId, List<Long> kbIds) {
        agentKnowledgeMapper.delete(Wrappers.<AiAgentKnowledgeEntity>lambdaQuery()
                .eq(AiAgentKnowledgeEntity::getAgentId, agentId));
        if (kbIds != null) {
            for (int i = 0; i < kbIds.size(); i++) {
                AiAgentKnowledgeEntity kb = new AiAgentKnowledgeEntity();
                kb.setAgentId(agentId);
                kb.setKnowledgeBaseId(kbIds.get(i));
                kb.setSortOrder(i);
                agentKnowledgeMapper.insert(kb);
            }
        }
    }

    private void saveBindings(Long agentId, List<Long> toolIds, List<Long> knowledgeBaseIds) {
        if (toolIds != null) {
            for (int i = 0; i < toolIds.size(); i++) {
                AiAgentToolEntity tool = new AiAgentToolEntity();
                tool.setAgentId(agentId);
                tool.setMcpToolId(toolIds.get(i));
                tool.setSortOrder(i);
                agentToolMapper.insert(tool);
            }
        }
        if (knowledgeBaseIds != null) {
            for (int i = 0; i < knowledgeBaseIds.size(); i++) {
                AiAgentKnowledgeEntity kb = new AiAgentKnowledgeEntity();
                kb.setAgentId(agentId);
                kb.setKnowledgeBaseId(knowledgeBaseIds.get(i));
                kb.setSortOrder(i);
                agentKnowledgeMapper.insert(kb);
            }
        }
    }
}
