package com.hify.agent.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentUpdateRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private String systemPrompt;

    @NotNull(message = "请选择模型配置")
    private Long modelConfigId;

    @DecimalMin("0.00") @DecimalMax("1.00")
    private BigDecimal temperature;

    @Min(1) @Max(32768)
    private Integer maxTokens;

    @Min(1) @Max(100)
    private Integer maxContextTurns;

    /** 绑定的知识库 id，传 null 表示不启用 RAG */
    private Long knowledgeBaseId;
}
