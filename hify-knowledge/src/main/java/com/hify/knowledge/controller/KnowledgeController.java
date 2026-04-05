package com.hify.knowledge.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.knowledge.dto.*;
import com.hify.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    // ── 知识库 ────────────────────────────────────────────────

    @PostMapping("/api/v1/knowledge-bases")
    public Result<KnowledgeBaseVO> createKb(@Valid @RequestBody KnowledgeBaseCreateRequest req) {
        return Result.ok(knowledgeService.createKb(req));
    }

    @GetMapping("/api/v1/knowledge-bases")
    public Result<PageResult<KnowledgeBaseVO>> listKb(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name) {
        return knowledgeService.listKb(page, pageSize, name);
    }

    @GetMapping("/api/v1/knowledge-bases/{id}")
    public Result<KnowledgeBaseVO> getKb(@PathVariable Long id) {
        return Result.ok(knowledgeService.getKb(id));
    }

    @PutMapping("/api/v1/knowledge-bases/{id}")
    public Result<KnowledgeBaseVO> updateKb(@PathVariable Long id,
                                             @RequestBody KnowledgeBaseUpdateRequest req) {
        return Result.ok(knowledgeService.updateKb(id, req));
    }

    @DeleteMapping("/api/v1/knowledge-bases/{id}")
    public Result<Void> deleteKb(@PathVariable Long id) {
        knowledgeService.deleteKb(id);
        return Result.ok();
    }

    // ── 文档 ──────────────────────────────────────────────────

    @PostMapping("/api/v1/knowledge-bases/{kbId}/documents")
    public Result<DocumentVO> uploadDocument(@PathVariable Long kbId,
                                              @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeService.uploadDocument(kbId, file));
    }

    @GetMapping("/api/v1/knowledge-bases/{kbId}/documents")
    public Result<PageResult<DocumentVO>> listDocuments(
            @PathVariable Long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return knowledgeService.listDocuments(kbId, page, pageSize);
    }

    @GetMapping("/api/v1/documents/{id}")
    public Result<DocumentVO> getDocument(@PathVariable Long id) {
        return Result.ok(knowledgeService.getDocument(id));
    }

    @GetMapping("/api/v1/documents/{id}/chunks")
    public Result<List<ChunkVO>> getChunks(@PathVariable Long id) {
        return Result.ok(knowledgeService.getChunks(id));
    }

    @DeleteMapping("/api/v1/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDocument(id);
        return Result.ok();
    }
}
