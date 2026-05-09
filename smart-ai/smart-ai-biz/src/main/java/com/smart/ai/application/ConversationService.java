package com.smart.ai.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.infrastructure.persistence.entity.AiConversationEntity;
import com.smart.ai.infrastructure.persistence.entity.AiMessageEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiConversationMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Conversation management service.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;

    public IPage<AiConversationEntity> pageByUser(Page<AiConversationEntity> page, Long userId) {
        return conversationMapper.selectPage(page, Wrappers.<AiConversationEntity>lambdaQuery()
                .eq(AiConversationEntity::getUserId, userId)
                .orderByDesc(AiConversationEntity::getPinned)
                .orderByDesc(AiConversationEntity::getUpdateTime));
    }

    public AiConversationEntity getById(Long id) {
        return conversationMapper.selectById(id);
    }

    public Long createConversation(Long userId, Long modelConfigId, Long agentId, String title) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setUserId(userId);
        entity.setModelConfigId(modelConfigId);
        entity.setAgentId(agentId);
        entity.setTitle(title != null ? title : "New Conversation");
        entity.setMessageCount(0);
        entity.setTotalTokens(0L);
        entity.setPinned(false);
        conversationMapper.insert(entity);
        return entity.getId();
    }

    public void updateTitle(Long id, String title) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(id);
        entity.setTitle(title);
        conversationMapper.updateById(entity);
    }

    public void togglePin(Long id) {
        AiConversationEntity existing = conversationMapper.selectById(id);
        if (existing != null) {
            AiConversationEntity update = new AiConversationEntity();
            update.setId(id);
            update.setPinned(!existing.getPinned());
            conversationMapper.updateById(update);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        messageMapper.delete(Wrappers.<AiMessageEntity>lambdaQuery()
                .eq(AiMessageEntity::getConversationId, id));
        conversationMapper.deleteById(id);
    }

    public List<AiMessageEntity> listMessages(Long conversationId) {
        return messageMapper.selectList(Wrappers.<AiMessageEntity>lambdaQuery()
                .eq(AiMessageEntity::getConversationId, conversationId)
                .orderByAsc(AiMessageEntity::getCreateTime));
    }

    public void incrementMessageCount(Long conversationId, int tokenCount) {
        AiConversationEntity existing = conversationMapper.selectById(conversationId);
        if (existing != null) {
            AiConversationEntity update = new AiConversationEntity();
            update.setId(conversationId);
            update.setMessageCount(existing.getMessageCount() + 1);
            update.setTotalTokens(existing.getTotalTokens() + tokenCount);
            conversationMapper.updateById(update);
        }
    }
}
