package com.hify.knowledge.dto;
import com.hify.knowledge.entity.Document;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class DocumentVO {
    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String fileType;
    private Long fileSize;
    private String status;
    private String errorMessage;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentVO from(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setKnowledgeBaseId(doc.getKnowledgeBaseId());
        vo.setName(doc.getName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setErrorMessage(doc.getErrorMessage() != null ? doc.getErrorMessage() : "");
        vo.setChunkCount(doc.getChunkCount() != null ? doc.getChunkCount() : 0);
        vo.setCreatedAt(doc.getCreatedAt());
        vo.setUpdatedAt(doc.getUpdatedAt());
        return vo;
    }
}
