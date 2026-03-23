package com.hify.agent.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentCreateRequest {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private String systemPrompt;

    @NotNull(message = "请选择模型配置")
    private Long modelConfigId;

    @DecimalMin("0.00") @DecimalMax("1.00")
    private BigDecimal temperature = new BigDecimal("0.70");

    @Min(1) @Max(32768)
    private Integer maxTokens = 2048;

    @Min(1) @Max(100)
    private Integer maxContextTurns = 10;

    private List<Long> toolIds;
}
