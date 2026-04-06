package com.hify.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class McpServerUpdateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "Endpoint 不能为空")
    private String endpoint;

    private String description;
    private Integer enabled;
}
