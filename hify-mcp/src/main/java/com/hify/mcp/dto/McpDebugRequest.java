package com.hify.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class McpDebugRequest {

    @NotBlank(message = "工具名不能为空")
    private String toolName;

    private Map<String, Object> arguments;
}
