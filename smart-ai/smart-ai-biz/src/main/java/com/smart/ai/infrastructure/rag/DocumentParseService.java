package com.smart.ai.infrastructure.rag;

import com.smart.ai.infrastructure.llm.EmbeddingModelFactory;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeBaseEntity;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeDocumentEntity;
import com.smart.ai.infrastructure.persistence.entity.AiKnowledgeSegmentEntity;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeBaseMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeDocumentMapper;
import com.smart.ai.infrastructure.persistence.mapper.AiKnowledgeSegmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing documents (PDF, Word, TXT, etc.) into text segments
 * and storing them as knowledge base segments for RAG retrieval.
 *
 * <p>Uses Apache Tika for document parsing, which supports 1000+ file formats.
 * After parsing, the text is split into chunks based on the knowledge base's
 * configured chunk size and overlap settings.
 *
 * <p>Vector embedding generation is handled separately by the
 * {@link RagRetrievalService} which reads segments and calls the embedding model.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AiKnowledgeDocumentMapper documentMapper;
    private final AiKnowledgeSegmentMapper segmentMapper;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final VectorStoreJdbcHelper vectorStore;

    /**
     * Embedding 接口单次请求的最大 input 数量。
     *
     * <p>取各家 OpenAI 兼容供应商的最严限制：
     * <ul>
     *   <li>阿里百炼 DashScope text-embedding-v1/v2/v3：单次最多 <b>10</b> 条（超出报 InvalidParameter）</li>
     *   <li>OpenAI text-embedding-3-*：单次最多 2048 条</li>
     *   <li>DeepSeek / Ollama：通常无明确限制</li>
     * </ul>
     * 取 10 作为通用安全值，避免针对每家做开关。
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    /**
     * Tika 单次提取的最大字符数上限（默认 5,000,000 ≈ 5M chars）。
     * 防止超大 PDF/Word 把整本书的字符全部塞进堆里把 JVM 拉爆。
     * 可通过配置覆盖。
     */
    @Value("${ai.parse.max-extract-chars:5000000}")
    private int maxExtractChars;

    /**
     * 单文件最大字节数（默认 50MB）。
     * Tika 解析 PDF/PPTX 时内存放大系数通常是 5-10 倍，
     * 50MB 文件最坏可能吃掉 500MB 堆，超过这个值直接拒绝避免 OOM。
     */
    @Value("${ai.parse.max-file-size-bytes:52428800}")
    private long maxFileSizeBytes;

    /**
     * 单文档最大切片数（默认 5000）。
     * 既限制内存占用，也避免向 embedding 接口刷爆 quota。
     */
    @Value("${ai.parse.max-chunks-per-doc:5000}")
    private int maxChunksPerDoc;

    /**
     * 期望的 embedding 维度，必须与 {@code ai_knowledge_segment.embedding} 列的 vector(N) 维度一致。
     * <ul>
     *   <li>1024：阿里百炼 text-embedding-v3 ✅ 当前默认</li>
     *   <li>1536：OpenAI text-embedding-3-small / 通义 v1/v2</li>
     *   <li>3072：OpenAI text-embedding-3-large</li>
     *   <li>768：Ollama nomic-embed-text</li>
     * </ul>
     * 切换 embedding 模型时必须同步修改本配置和数据库列类型。
     */
    @Value("${ai.parse.embedding-dim:1024}")
    private int expectedEmbeddingDim;

    /** 与 {@code FileUploadController} 保持一致：本地文件物理存放根目录。 */
    @Value("${ai.upload.base-dir:./ai-upload}")
    private String uploadBaseDir;

    /**
     * 与 {@code FileUploadController} 保持一致：上传成功后回写到 fileUrl 的访问前缀。
     * 例如 "/ai/file/download"，对应物理路径 {@code uploadBaseDir} 下的子目录。
     */
    @Value("${ai.upload.access-prefix:/ai/file/download}")
    private String uploadAccessPrefix;

    /**
     * Asynchronously parse a document, split it into segments, and persist them.
     *
     * @param documentId the document record ID
     */
    @Async
    public void parseDocumentAsync(Long documentId) {
        AiKnowledgeDocumentEntity document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Document not found: {}", documentId);
            return;
        }

        AiKnowledgeBaseEntity knowledgeBase = knowledgeBaseMapper.selectById(document.getKbId());
        if (knowledgeBase == null) {
            updateDocumentStatus(document, "FAILED", "Knowledge base not found");
            return;
        }

        updateDocumentStatus(document, "PARSING", null);

        try {
            // Step 1: Parse document content using Tika
            String textContent = extractText(document.getFileUrl());
            if (textContent == null || textContent.isBlank()) {
                updateDocumentStatus(document, "FAILED", "No text content extracted from document");
                return;
            }

            // Step 2: Split text into chunks
            int chunkSize = knowledgeBase.getChunkSize() != null ? knowledgeBase.getChunkSize() : 500;
            int chunkOverlap = knowledgeBase.getChunkOverlap() != null ? knowledgeBase.getChunkOverlap() : 50;
            List<String> chunks = splitText(textContent, chunkSize, chunkOverlap);
            // 提前释放大文本对象，便于后续 GC 回收
            //noinspection UnusedAssignment
            textContent = null;

            // 防御性截断：避免一份文档生成几万个 chunk 把 embedding/向量库刷爆
            if (chunks.size() > maxChunksPerDoc) {
                log.warn("Document {} produced {} chunks, truncating to {} (configured limit)",
                        documentId, chunks.size(), maxChunksPerDoc);
                chunks = new ArrayList<>(chunks.subList(0, maxChunksPerDoc));
            }

            // Step 3: Delete existing segments for this document (re-parse scenario)
            segmentMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiKnowledgeSegmentEntity>()
                            .eq(AiKnowledgeSegmentEntity::getDocumentId, documentId));

            // Step 4: Generate embeddings (batched) — fall back to no-embedding if not configured
            Long embeddingModelId = knowledgeBase.getEmbeddingModelId();
            List<float[]> embeddings = null;
            if (embeddingModelId != null) {
                try {
                    EmbeddingModel embeddingModel = embeddingModelFactory.getOrCreate(embeddingModelId);
                    embeddings = embedInBatches(embeddingModel, chunks);
                    // 维度预检：和 ai_knowledge_segment.embedding 列维度对齐，否则插入会被 PG 拒绝
                    if (!embeddings.isEmpty() && embeddings.get(0) != null) {
                        int actualDim = embeddings.get(0).length;
                        if (actualDim != expectedEmbeddingDim) {
                            throw new IllegalStateException(String.format(
                                    "Embedding dimension mismatch: model returned %d dim, but ai_knowledge_segment.embedding "
                                            + "is vector(%d). Either switch to a %d-dim model or run "
                                            + "ALTER TABLE ai_knowledge_segment ALTER COLUMN embedding TYPE vector(%d) and rebuild HNSW index.",
                                    actualDim, expectedEmbeddingDim, expectedEmbeddingDim, actualDim));
                        }
                    }
                    log.info("Generated {} embeddings for document {} (dim={})",
                            embeddings.size(), documentId,
                            embeddings.isEmpty() ? 0 : embeddings.get(0).length);
                } catch (Exception e) {
                    log.warn("Embedding generation failed for document {}: {}. Falling back to keyword-only.",
                            documentId, e.getMessage());
                    embeddings = null;
                }
            }

            // Step 5: Persist segments (with or without embedding)
            int totalTokens = 0;
            Long tenantId = document.getTenantId() != null ? document.getTenantId() : 1L;
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                int tokenCount = estimateTokenCount(chunk);
                if (embeddings != null && i < embeddings.size() && embeddings.get(i) != null) {
                    vectorStore.insertSegmentWithEmbedding(
                            document.getKbId(), documentId, i, chunk, tokenCount,
                            embeddings.get(i), tenantId);
                } else {
                    vectorStore.insertSegmentWithoutEmbedding(
                            document.getKbId(), documentId, i, chunk, tokenCount, tenantId);
                }
                totalTokens += tokenCount;
            }

            // Step 6: Update document metadata
            document.setSegmentCount(chunks.size());
            document.setTokenCount(totalTokens);
            updateDocumentStatus(document, "COMPLETED", null);

            // Step 7: Update knowledge base statistics
            updateKnowledgeBaseStats(document.getKbId());

            log.info("Document {} parsed successfully: {} segments, {} tokens",
                    documentId, chunks.size(), totalTokens);

        } catch (Exception e) {
            log.error("Failed to parse document {}: {}", documentId, e.getMessage(), e);
            updateDocumentStatus(document, "FAILED", e.getMessage());
        }
    }

    /**
     * Generate embeddings for a list of text chunks in batches.
     *
     * <p>Spring AI's {@link EmbeddingModel#embed(List)} accepts batches; we further
     * chunk into {@link #EMBEDDING_BATCH_SIZE} to avoid timeouts on large documents.
     */
    private List<float[]> embedInBatches(EmbeddingModel embeddingModel, List<String> chunks) {
        List<float[]> result = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
            List<String> batch = chunks.subList(start, end);
            EmbeddingResponse response = embeddingModel.embedForResponse(batch);
            response.getResults().forEach(r -> result.add(r.getOutput()));
        }
        return result;
    }

    /**
     * Extract text content from a document URL using Apache Tika.
     *
     * <p>支持三种 fileUrl 形式：
     * <ol>
     *   <li>绝对 HTTP(S) URL：直接 openStream（适用于外链、OSS 直链等）</li>
     *   <li>file: 协议绝对 URI：直接 openStream</li>
     *   <li>本地访问路径（以 {@link #uploadAccessPrefix} 开头的相对路径）：
     *       映射到 {@link #uploadBaseDir} 下的物理文件直接读取，避免回调 HTTP 接口带鉴权问题</li>
     * </ol>
     */
    private String extractText(String fileUrl) throws IOException, TikaException, SAXException {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL is empty");
        }

        // Case 1：绝对 URL（http/https/file）
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://") || fileUrl.startsWith("file:")) {
            try (InputStream inputStream = URI.create(fileUrl).toURL().openStream()) {
                return parseWithLimit(inputStream);
            }
        }

        // Case 2：本地相对路径，映射到上传根目录下的物理文件
        Path filePath = resolveLocalUploadPath(fileUrl);
        if (filePath == null || !Files.exists(filePath)) {
            throw new IOException("File not found for url: " + fileUrl);
        }
        long size = Files.size(filePath);
        if (size > maxFileSizeBytes) {
            throw new IOException("File too large for parsing: " + size + " bytes (limit "
                    + maxFileSizeBytes + " bytes). Adjust ai.parse.max-file-size-bytes if intentional.");
        }
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return parseWithLimit(inputStream);
        }
    }

    /**
     * 用 {@link BodyContentHandler}(maxExtractChars) 替代 {@link org.apache.tika.Tika#parseToString}，
     * 显式限制提取的字符上限，避免大文档整本读入堆里。
     *
     * <p>{@code Tika.parseToString} 默认上限只有 100,000 字符，但解析过程中
     * Tika 会先把整个文档对象（PDF 字体、字形、图片元数据等）加载进内存，
     * 真正的内存爆点在解析阶段而不是字符串拼接。把上限调大并显式调用 AutoDetectParser
     * 可以让我们在内存压力可控的前提下提取较长文本。
     */
    private String parseWithLimit(InputStream inputStream) throws IOException, TikaException, SAXException {
        BodyContentHandler handler = new BodyContentHandler(maxExtractChars);
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        parser.parse(inputStream, handler, metadata, new ParseContext());
        return handler.toString();
    }

    /**
     * 把上传时生成的访问 URL（如 {@code /ai/file/download/default/2026/05/06/xxx.pdf}）
     * 还原为本地物理路径。返回 null 表示无法识别。
     */
    private Path resolveLocalUploadPath(String fileUrl) {
        String prefix = uploadAccessPrefix == null ? "" : uploadAccessPrefix;
        String relative;
        if (!prefix.isEmpty() && fileUrl.startsWith(prefix)) {
            relative = fileUrl.substring(prefix.length());
        } else {
            // 兼容历史/异常数据：直接当作 baseDir 下的相对路径处理
            relative = fileUrl;
        }
        // 去掉前导 '/' 避免被 resolve 误判为绝对路径
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isBlank()) {
            return null;
        }

        Path basePath = Paths.get(uploadBaseDir).toAbsolutePath().normalize();
        Path target = basePath.resolve(relative).normalize();
        // Security: 防止路径穿越
        if (!target.startsWith(basePath)) {
            log.warn("Refuse to read file outside upload base dir: {}", target);
            return null;
        }
        return target;
    }

    /**
     * Split text into chunks with overlap using a simple character-based strategy.
     *
     * <p>The algorithm splits on sentence boundaries (。.！!？?) when possible,
     * falling back to character-level splitting if sentences are too long.
     *
     * <p><b>关键不变量：start 必须严格单调递增。</b>
     * 历史 bug：当句子边界回退导致 end - overlap &lt;= 上一轮 start 时，会无限循环
     * 把同一片文本反复塞进 list，2.4GB 堆瞬间打满。
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        // 防御性参数校验
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        if (chunkSize <= 0) {
            chunkSize = 500;
        }
        // overlap 必须严格小于 chunkSize，否则 step 可能为 0 导致死循环
        if (overlap < 0 || overlap >= chunkSize) {
            overlap = Math.max(0, chunkSize / 10);
        }

        if (text.length() <= chunkSize) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
            return chunks;
        }

        int start = 0;
        int textLen = text.length();
        while (start < textLen) {
            int end = Math.min(start + chunkSize, textLen);

            // Try to find a sentence boundary near the chunk end (only if not at end of text)
            if (end < textLen) {
                int sentenceBoundary = findSentenceBoundary(text, start + (chunkSize / 2), end);
                // 只有当边界明显推进（至少超过 start + chunkSize/2）时才回退 end，
                // 避免把窗口缩得太小导致后续 step 异常
                if (sentenceBoundary > start + (chunkSize / 2)) {
                    end = sentenceBoundary;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 关键：保证 start 严格单调递增
            // 1. 若 end 已到文本末尾，结束循环
            if (end >= textLen) {
                break;
            }
            // 2. 计算下一轮起点：理想情况下回退 overlap 个字符做语义衔接
            int nextStart = end - overlap;
            // 3. 兜底：必须比当前 start 至少前进 1 个字符，否则强制跳到 end
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;

            // 4. 双保险：超过预期最大切片数直接中断（理论上 chunks.size 上限 = textLen / (chunkSize - overlap)）
            if (chunks.size() > maxChunksPerDoc) {
                log.warn("splitText hit chunks safety cap {} for text length {}, stopping early.",
                        maxChunksPerDoc, textLen);
                break;
            }
        }

        return chunks;
    }

    /**
     * Find the nearest sentence boundary (。.！!？?) within the given range.
     */
    private int findSentenceBoundary(String text, int searchStart, int searchEnd) {
        String sentenceEnders = "。.！!？?\n";
        int bestBoundary = -1;

        for (int i = searchEnd - 1; i >= searchStart; i--) {
            if (sentenceEnders.indexOf(text.charAt(i)) >= 0) {
                bestBoundary = i + 1;
                break;
            }
        }

        return bestBoundary;
    }

    /**
     * Rough token count estimation (CJK characters count as ~1.5 tokens,
     * English words as ~1.3 tokens, simple approximation).
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;

        int cjkCount = 0;
        int otherCount = 0;
        for (char ch : text.toCharArray()) {
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN
                    || Character.UnicodeScript.of(ch) == Character.UnicodeScript.KATAKANA
                    || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HIRAGANA) {
                cjkCount++;
            } else if (!Character.isWhitespace(ch)) {
                otherCount++;
            }
        }

        // CJK: ~1.5 tokens per character; Latin: ~4 characters per token
        return (int) (cjkCount * 1.5 + otherCount / 4.0);
    }

    private void updateDocumentStatus(AiKnowledgeDocumentEntity document, String status, String errorMsg) {
        document.setParseStatus(status);
        document.setErrorMsg(errorMsg);
        documentMapper.updateById(document);
    }

    private void updateKnowledgeBaseStats(Long kbId) {
        Long docCount = documentMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiKnowledgeDocumentEntity>()
                        .eq(AiKnowledgeDocumentEntity::getKbId, kbId));
        Long segCount = segmentMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiKnowledgeSegmentEntity>()
                        .eq(AiKnowledgeSegmentEntity::getKbId, kbId));

        AiKnowledgeBaseEntity update = new AiKnowledgeBaseEntity();
        update.setId(kbId);
        update.setDocumentCount(docCount.intValue());
        update.setSegmentCount(segCount.intValue());
        knowledgeBaseMapper.updateById(update);
    }
}
