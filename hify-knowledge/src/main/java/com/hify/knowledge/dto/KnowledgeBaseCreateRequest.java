package com.hify.knowledge.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KnowledgeBaseCreateRequest {
    @NotBlank private String name;
    private String description;
}
