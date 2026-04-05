package com.hify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.knowledge.dto.*;
import com.hify.knowledge.entity.Document;
import com.hify.knowledge.entity.KnowledgeBase;
import com.hify.knowledge.mapper.DocumentMapper;
import com.hify.knowledge.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final Executor asyncExecutor;

    // Mock chunk 存储（替代 pgvector）
    private static final ConcurrentHashMap<Long, List<ChunkVO>> MOCK_CHUNKS = new ConcurrentHashMap<>();
    // 暂存原始文件内容，processDocument 后清除
    private static final ConcurrentHashMap<Long, String> DOC_RAW_TEXT = new ConcurrentHashMap<>();
    private static final List<String> ALLOWED_TYPES = Arrays.asList("txt", "md", "pdf");
    private static final long MAX_SIZE = 10 * 1024 * 1024L; // 10MB

    public KnowledgeServiceImpl(KnowledgeBaseMapper kbMapper,
                                DocumentMapper documentMapper,
                                @Qualifier("asyncExecutor") Executor asyncExecutor) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.asyncExecutor = asyncExecutor;
    }

    // ── 知识库 CRUD ───────────────────────────────────────────

    @Override
    public KnowledgeBaseVO createKb(KnowledgeBaseCreateRequest req) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(req.getName());
        kb.setDescription(req.getDescription() != null ? req.getDescription() : "");
        kb.setEnabled(1);
        kbMapper.insert(kb);
        return KnowledgeBaseVO.from(kb);
    }

    @Override
    public Result<PageResult<KnowledgeBaseVO>> listKb(int page, int pageSize, String name) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
            .like(name != null && !name.isBlank(), KnowledgeBase::getName, name)
            .orderByDesc(KnowledgeBase::getCreatedAt);
        int size = Math.min(pageSize, 100);
        var p = kbMapper.selectPage(new Page<>(page, size), wrapper);
        List<KnowledgeBaseVO> list = p.getRecords().stream()
            .map(KnowledgeBaseVO::from).collect(Collectors.toList());
        return PageResult.of(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    @Override
    public KnowledgeBaseVO getKb(Long id) {
        return KnowledgeBaseVO.from(getKbOrThrow(id));
    }

    @Override
    public KnowledgeBaseVO updateKb(Long id, KnowledgeBaseUpdateRequest req) {
        KnowledgeBase kb = getKbOrThrow(id);
        if (req.getName() != null) kb.setName(req.getName());
        if (req.getDescription() != null) kb.setDescription(req.getDescription());
        if (req.getEnabled() != null) kb.setEnabled(req.getEnabled());
        kbMapper.updateById(kb);
        return KnowledgeBaseVO.from(kb);
    }

    @Override
    @Transactional
    public void deleteKb(Long id) {
        getKbOrThrow(id);
        // 逻辑删除下属文档
        List<Document> docs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>().eq(Document::getKnowledgeBaseId, id));
        for (Document doc : docs) {
            MOCK_CHUNKS.remove(doc.getId());
            documentMapper.deleteById(doc.getId());
        }
        kbMapper.deleteById(id);
    }

    // ── 文档 CRUD ─────────────────────────────────────────────

    @Override
    public DocumentVO uploadDocument(Long kbId, MultipartFile file) {
        getKbOrThrow(kbId);

        // 校验文件类型
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
            : "";
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }

        // 读取文件内容（txt/md 直接读文本，pdf 降级为文件名占位）
        String rawText = "";
        try {
            if ("txt".equals(ext) || "md".equals(ext)) {
                rawText = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } else {
                rawText = "[PDF 文件：" + originalName + "，Mock 模式暂不解析 PDF 内容]";
            }
        } catch (IOException e) {
            log.warn("读取文件内容失败: {}", e.getMessage());
        }
        final String rawTextFinal = rawText;

        // 写 document 记录
        Document doc = new Document();
        doc.setKnowledgeBaseId(kbId);
        doc.setName(originalName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setStatus("PENDING");
        doc.setErrorMessage("");
        doc.setChunkCount(0);
        documentMapper.insert(doc);

        Long docId = doc.getId();
        if (!rawTextFinal.isBlank()) {
            DOC_RAW_TEXT.put(docId, rawTextFinal);
        }
        asyncExecutor.execute(() -> processDocument(docId));

        return DocumentVO.from(doc);
    }

    @Override
    public Result<PageResult<DocumentVO>> listDocuments(Long kbId, int page, int pageSize) {
        getKbOrThrow(kbId);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
            .eq(Document::getKnowledgeBaseId, kbId)
            .orderByDesc(Document::getCreatedAt);
        int size = Math.min(pageSize, 100);
        var p = documentMapper.selectPage(new Page<>(page, size), wrapper);
        List<DocumentVO> list = p.getRecords().stream()
            .map(DocumentVO::from).collect(Collectors.toList());
        return PageResult.of(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    @Override
    public DocumentVO getDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        return DocumentVO.from(doc);
    }

    @Override
    public List<ChunkVO> getChunks(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        return MOCK_CHUNKS.getOrDefault(documentId, List.of());
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
        MOCK_CHUNKS.remove(id);
        documentMapper.deleteById(id);
    }

    // ── RAG 检索（Mock）──────────────────────────────────────

    @Override
    public List<ChunkVO> searchChunks(Long knowledgeBaseId, String query, int topK) {
        // Mock 实现：收集该知识库下所有 DONE 文档的 chunk，取前 topK 条返回
        // 真实实现应调用 Embedding API + pgvector 相似度查询
        List<Document> docs = documentMapper.selectList(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .eq(Document::getStatus, "DONE"));

        List<ChunkVO> all = new ArrayList<>();
        for (Document doc : docs) {
            List<ChunkVO> chunks = MOCK_CHUNKS.getOrDefault(doc.getId(), List.of());
            all.addAll(chunks);
        }

        // Mock 相似度：随机打乱后取前 topK（真实场景是按向量余弦距离排序）
        java.util.Collections.shuffle(all, new Random(query.hashCode()));
        List<ChunkVO> result = all.stream().limit(topK).toList();
        log.info("RAG mock 检索 kbId={} query='{}' 命中 {}/{} 条", knowledgeBaseId, query, result.size(), all.size());
        return result;
    }

    // ── 管线处理（Mock）──────────────────────────────────────

    private void processDocument(Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) return;
        try {
            // step1: PROCESSING
            doc.setStatus("PROCESSING");
            documentMapper.updateById(doc);

            // step2: 模拟处理耗时
            Thread.sleep(2000 + new Random().nextInt(2000));

            // step3: 生成 chunks（优先用真实文件内容，按段落/换行切割）
            String rawText = DOC_RAW_TEXT.remove(documentId);
            List<ChunkVO> chunks = new ArrayList<>();
            if (rawText != null && !rawText.isBlank()) {
                // 按段落切割（连续空行）
                String[] paragraphs = rawText.split("\\n{2,}");
                for (int i = 0; i < paragraphs.length; i++) {
                    String para = paragraphs[i].trim();
                    if (para.isBlank()) continue;
                    ChunkVO c = new ChunkVO();
                    c.setId((long) (documentId * 1000 + i));
                    c.setDocumentId(documentId);
                    c.setChunkIndex(i);
                    c.setContent(para);
                    c.setTokenCount(para.length() / 2 + 1);
                    chunks.add(c);
                }
            }
            // 如果没有内容（如 PDF 占位），生成一条说明
            if (chunks.isEmpty()) {
                ChunkVO c = new ChunkVO();
                c.setId(documentId * 1000);
                c.setDocumentId(documentId);
                c.setChunkIndex(0);
                c.setContent(String.format("【%s】（Mock 模式：文档内容未能解析）", doc.getName()));
                c.setTokenCount(20);
                chunks.add(c);
            }
            int chunkCount = chunks.size();
            MOCK_CHUNKS.put(documentId, chunks);

            // step4: 更新状态
            doc.setChunkCount(chunkCount);
            doc.setStatus("DONE");
            documentMapper.updateById(doc);

            log.info("文档处理完成 docId={}, chunks={}", documentId, chunkCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("文档处理失败 docId={}", documentId, e);
            doc.setStatus("FAILED");
            doc.setErrorMessage(e.getMessage() != null ? e.getMessage() : "处理失败");
            documentMapper.updateById(doc);
        }
    }

    private KnowledgeBase getKbOrThrow(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) throw new BizException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        return kb;
    }
}
