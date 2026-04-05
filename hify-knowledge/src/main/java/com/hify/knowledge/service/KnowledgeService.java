package com.hify.knowledge.service;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.knowledge.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {
    KnowledgeBaseVO createKb(KnowledgeBaseCreateRequest req);
    Result<PageResult<KnowledgeBaseVO>> listKb(int page, int pageSize, String name);
    KnowledgeBaseVO getKb(Long id);
    KnowledgeBaseVO updateKb(Long id, KnowledgeBaseUpdateRequest req);
    void deleteKb(Long id);

    DocumentVO uploadDocument(Long kbId, MultipartFile file);
    Result<PageResult<DocumentVO>> listDocuments(Long kbId, int page, int pageSize);
    DocumentVO getDocument(Long id);
    List<ChunkVO> getChunks(Long documentId);
    void deleteDocument(Long id);

    /** RAG 检索：从知识库中找与 query 最相关的 topK 个 chunk（mock 实现直接返回前 topK 条） */
    List<ChunkVO> searchChunks(Long knowledgeBaseId, String query, int topK);
}
