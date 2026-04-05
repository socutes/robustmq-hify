package com.hify.knowledge.dto;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChunkVO {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
}
