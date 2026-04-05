package com.hify.knowledge.dto;
import com.hify.knowledge.entity.KnowledgeBase;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class KnowledgeBaseVO {
    private Long id;
    private String name;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeBaseVO from(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription() != null ? kb.getDescription() : "");
        vo.setEnabled(kb.getEnabled());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());
        return vo;
    }
}
