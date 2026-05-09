package com.smart.ai.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.ai.api.dto.KnowledgeBaseCmd;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeBaseEntity;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeDocumentEntity;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeSegmentEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeBaseMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeDocumentMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeSegmentMapper;
import com.smart.ai.infrastructure.rag.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RAG knowledge base management service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiKnowledgeDocumentMapper docMapper;
    private final AiKnowledgeSegmentMapper segMapper;
    private final DocumentParseService documentParseService;

    public IPage<AiKnowledgeBaseEntity> page(Page<AiKnowledgeBaseEntity> page, String keyword) {
        return kbMapper.selectPage(page, Wrappers.<AiKnowledgeBaseEntity>lambdaQuery()
                .like(keyword != null, AiKnowledgeBaseEntity::getKbName, keyword)
                .orderByDesc(AiKnowledgeBaseEntity::getCreateTime));
    }

    public AiKnowledgeBaseEntity getById(Long id) {
        return kbMapper.selectById(id);
    }

    public Long save(KnowledgeBaseCmd cmd) {
        AiKnowledgeBaseEntity entity = new AiKnowledgeBaseEntity();
        BeanUtils.copyProperties(cmd, entity);
        entity.setDocumentCount(0);
        entity.setSegmentCount(0);
        kbMapper.insert(entity);
        return entity.getId();
    }

    public void update(KnowledgeBaseCmd cmd) {
        AiKnowledgeBaseEntity entity = new AiKnowledgeBaseEntity();
        BeanUtils.copyProperties(cmd, entity);
        kbMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        segMapper.delete(Wrappers.<AiKnowledgeSegmentEntity>lambdaQuery()
                .eq(AiKnowledgeSegmentEntity::getKbId, id));
        docMapper.delete(Wrappers.<AiKnowledgeDocumentEntity>lambdaQuery()
                .eq(AiKnowledgeDocumentEntity::getKbId, id));
        kbMapper.deleteById(id);
    }

    public IPage<AiKnowledgeDocumentEntity> pageDocuments(Page<AiKnowledgeDocumentEntity> page, Long kbId) {
        return docMapper.selectPage(page, Wrappers.<AiKnowledgeDocumentEntity>lambdaQuery()
                .eq(AiKnowledgeDocumentEntity::getKbId, kbId)
                .orderByDesc(AiKnowledgeDocumentEntity::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long documentId) {
        AiKnowledgeDocumentEntity document = docMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        segMapper.delete(Wrappers.<AiKnowledgeSegmentEntity>lambdaQuery()
                .eq(AiKnowledgeSegmentEntity::getDocumentId, documentId));
        docMapper.deleteById(documentId);

        // Update knowledge base statistics
        Long kbId = document.getKbId();
        Long docCount = docMapper.selectCount(Wrappers.<AiKnowledgeDocumentEntity>lambdaQuery()
                .eq(AiKnowledgeDocumentEntity::getKbId, kbId));
        Long segCount = segMapper.selectCount(Wrappers.<AiKnowledgeSegmentEntity>lambdaQuery()
                .eq(AiKnowledgeSegmentEntity::getKbId, kbId));
        AiKnowledgeBaseEntity kbUpdate = new AiKnowledgeBaseEntity();
        kbUpdate.setId(kbId);
        kbUpdate.setDocumentCount(docCount.intValue());
        kbUpdate.setSegmentCount(segCount.intValue());
        kbMapper.updateById(kbUpdate);
    }

    public List<AiKnowledgeSegmentEntity> listSegments(Long documentId) {
        return segMapper.selectList(Wrappers.<AiKnowledgeSegmentEntity>lambdaQuery()
                .eq(AiKnowledgeSegmentEntity::getDocumentId, documentId)
                .orderByAsc(AiKnowledgeSegmentEntity::getSegmentIndex));
    }

    /**
     * Reindex all documents in a knowledge base — re-parse + re-embed every doc.
     *
     * <p>Use cases:
     * <ul>
     *   <li>Embedding model is newly configured (segments previously stored without vectors)</li>
     *   <li>Embedding model changed (existing vectors are dim-mismatched and useless)</li>
     *   <li>Chunk size / overlap changed</li>
     * </ul>
     *
     * <p>Each document is re-parsed asynchronously; this method returns immediately
     * with the count of documents queued.
     *
     * @param kbId knowledge base id
     * @return number of documents queued for re-parsing
     */
    public int reindexAll(Long kbId) {
        List<AiKnowledgeDocumentEntity> documents = docMapper.selectList(
                Wrappers.<AiKnowledgeDocumentEntity>lambdaQuery()
                        .eq(AiKnowledgeDocumentEntity::getKbId, kbId));
        if (documents.isEmpty()) {
            return 0;
        }
        for (AiKnowledgeDocumentEntity doc : documents) {
            // Reset status so the UI shows "PARSING" immediately; the async task will mark COMPLETED/FAILED
            doc.setParseStatus("PENDING");
            doc.setErrorMsg(null);
            docMapper.updateById(doc);
            documentParseService.parseDocumentAsync(doc.getId());
        }
        log.info("Queued {} documents for reindex in knowledge base {}", documents.size(), kbId);
        return documents.size();
    }
}
