package com.smart.nl2sql.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.nl2sql.api.dto.Nl2SqlChatCmd;
import com.smart.nl2sql.api.dto.Nl2SqlChatVO;
import com.smart.nl2sql.api.dto.Nl2SqlSessionDTO;
import com.smart.nl2sql.api.dto.SqlEditCmd;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlMessageEntity;
import com.smart.nl2sql.infrastructure.persistence.entity.Nl2sqlSessionEntity;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlMessageMapper;
import com.smart.nl2sql.infrastructure.persistence.mapper.Nl2sqlSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlChatService {

    private final Nl2sqlSessionMapper sessionMapper;
    private final Nl2sqlMessageMapper messageMapper;

    public Page<Nl2sqlSessionEntity> page(Page<Nl2sqlSessionEntity> page, Long datasetId) {
        LambdaQueryWrapper<Nl2sqlSessionEntity> wrapper = new LambdaQueryWrapper<>();
        if (datasetId != null) {
            wrapper.eq(Nl2sqlSessionEntity::getDatasetId, datasetId);
        }
        wrapper.orderByDesc(Nl2sqlSessionEntity::getUpdateTime);
        return sessionMapper.selectPage(page, wrapper);
    }

    public Nl2SqlSessionDTO getSessionDetail(Long sessionId) {
        Nl2sqlSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        Nl2SqlSessionDTO dto = new Nl2SqlSessionDTO();
        BeanUtils.copyProperties(session, dto);
        return dto;
    }

    @Transactional
    public Long createSession(Nl2SqlSessionDTO dto, Long userId) {
        Nl2sqlSessionEntity session = new Nl2sqlSessionEntity();
        session.setDatasetId(dto.getDatasetId());
        session.setModelId(dto.getModelId());
        session.setUserId(userId);
        session.setTitle(dto.getTitle() != null && !dto.getTitle().isBlank() ? dto.getTitle() : "新会话");
        sessionMapper.insert(session);
        return session.getId();
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<Nl2sqlMessageEntity>()
                .eq(Nl2sqlMessageEntity::getSessionId, sessionId));
    }

    @Transactional
    public Nl2sqlMessageEntity addMessage(Long sessionId, String role, String content) {
        Nl2sqlMessageEntity message = new Nl2sqlMessageEntity();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        messageMapper.insert(message);
        touchSession(sessionId);
        return message;
    }

    private void touchSession(Long sessionId) {
        Nl2sqlSessionEntity session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        Nl2sqlSessionEntity session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setTitle(title);
            sessionMapper.updateById(session);
        }
    }

    public Nl2sqlMessageEntity getMessage(Long messageId) {
        return messageMapper.selectById(messageId);
    }

    @Transactional
    public void updateMessage(Nl2sqlMessageEntity message) {
        messageMapper.updateById(message);
    }

    public List<Nl2SqlChatVO> getMessages(Long sessionId) {
        List<Nl2sqlMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Nl2sqlMessageEntity>()
                        .eq(Nl2sqlMessageEntity::getSessionId, sessionId)
                        .orderByAsc(Nl2sqlMessageEntity::getCreateTime));

        // ⚠️ 这里**不能**用 BeanUtils.copyProperties：
        //   Entity 与 VO 有 4 个字段名/类型不一致，会导致回放历史时丢字段：
        //     entity.role           → vo.type           （名字不同 → role/type 丢失）
        //     entity.generatedSql   → vo.sql            （名字不同 → SQL 丢失）
        //     entity.id             → vo.messageId      （名字不同 → messageId 丢失，前端重跑/换图按钮失效）
        //     entity.executionTime  → vo.executionTime  （Integer vs Long → 静默丢失）
        //   之前用 BeanUtils 时，历史会话只能看到「数据洞察」，
        //   因为只有 dataInsight/queryResult/chartConfig/chartType/dimensions/measures 这几个字段名同名同类型才被拷贝过去。
        return messages.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * Entity → VO 显式映射。
     * 单独抽出来是为了让「历史回放」与「实时返回」走同一份字段映射逻辑，避免漂移。
     */
    private Nl2SqlChatVO toVO(Nl2sqlMessageEntity msg) {
        Nl2SqlChatVO vo = new Nl2SqlChatVO();
        vo.setMessageId(msg.getId());
        vo.setSessionId(msg.getSessionId());
        vo.setType(msg.getRole());
        vo.setContent(msg.getContent());
        vo.setSql(msg.getGeneratedSql());
        vo.setQueryResult(msg.getQueryResult());
        vo.setResultCount(msg.getResultCount());
        vo.setExecutionTime(msg.getExecutionTime() == null ? null : msg.getExecutionTime().longValue());
        vo.setChartType(msg.getChartType());
        vo.setChartConfig(msg.getChartConfig());
        vo.setDimensions(msg.getDimensions());
        vo.setMeasures(msg.getMeasures());
        vo.setDataInsight(msg.getDataInsight());
        vo.setErrorMessage(msg.getErrorMessage());
        return vo;
    }

}
